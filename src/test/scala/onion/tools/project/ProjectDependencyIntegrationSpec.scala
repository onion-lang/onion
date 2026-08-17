package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.Path

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * End-to-end cover for `[dependencies]`: a project that names a Maven coordinate must
 * compile against that jar and then *run* against it.
 *
 * Before this, `ProjectBuilder` passed `classPath = Seq.empty` to the compiler and
 * `onion.toml` rejected every key outside `[package]`, so a project could not reference a
 * third-party jar at all — while the README described Onion as a language that "runs on the
 * JVM and calls Java directly".
 *
 * The artifacts come from [[FixtureMavenRepository]] rather than the network, so this runs
 * deterministically and with the network down.
 */
class ProjectDependencyIntegrationSpec extends AnyFunSuite with Matchers:

  test("a project compiles and runs against a resolved dependency and its transitive"):
    // The value travels back through a system property rather than stdout: the program runs
    // in-process and `IO::println` writes to the real `System.out`, which the runner does not
    // redirect. `ProjectRunIntegrationSpec` uses the same trick for the same reason.
    val key = s"onion.project.dependency.${java.util.UUID.randomUUID()}"
    val project = fixture(
      FixtureMavenRepository.publish(),
      s"""import { ${FixtureMavenRepository.Group}.* }
         |System::setProperty("$key", Greeter::greet())
         |""".stripMargin
    )

    try
      val result = invoke(project)

      withClue(s"stderr was: ${result.stderr}") {
        result.exitCode shouldBe 0
      }
      // `greet` calls into `core`, so this only holds if the transitive was on both the
      // compile classpath and the run classpath.
      System.getProperty(key) shouldBe FixtureMavenRepository.Greeting
    finally System.clearProperty(key)

  test("the resolved coordinates include the transitive, which is what the fingerprint sees"):
    val repository = FixtureMavenRepository.publish()
    val manifest = ProjectManifest.load(
      fixture(repository, "IO::println(\"unused\")\n").resolve("onion.toml")).toOption.value

    val resolved = DependencyResolver.resolve(manifest.dependencies, manifest.repositories)
      .toOption.value

    resolved.coordinates should contain allOf (
      s"${FixtureMavenRepository.Group}:greeter:1.0.0",
      s"${FixtureMavenRepository.Group}:core:1.0.0"
    )
    resolved.classpath should have size 2

  test("an unresolvable coordinate fails with the coordinate named"):
    val repository = FixtureMavenRepository.publish()
    val error = DependencyResolver.resolve(
      Seq(Dependency(FixtureMavenRepository.Group, "absent", "9.9.9")),
      Seq(repository.toUri.toString)
    ).left.toOption.value

    error.message should startWith("Could not resolve dependencies:")
    error.message should include("absent")

  test("a project declaring no dependencies resolves to an empty classpath"):
    DependencyResolver.resolve(Seq.empty) shouldBe Right(ResolvedDependencies.empty)

  test("a build writes onion.lock, pinning the transitive nobody wrote down"):
    val project = fixture(FixtureMavenRepository.publish(), "IO::println(\"hi\")\n")

    invoke(project).exitCode shouldBe 0

    val lock = Files.readString(project.resolve("onion.lock"), UTF_8)
    // The direct dependency is in `onion.toml` already; the transitive is the one that
    // changes under a project without the manifest changing a byte.
    lock should include(s"${FixtureMavenRepository.Group}:core:1.0.0")
    lock should include("[[artifact]]")

  test("a second build honours the lock rather than resolving afresh"):
    val project = fixture(FixtureMavenRepository.publish(), "IO::println(\"hi\")\n")
    invoke(project).exitCode shouldBe 0
    val first = Files.readString(project.resolve("onion.lock"), UTF_8)

    // Force a recompile so resolution runs again rather than being served from the cache.
    Files.writeString(project.resolve("src").resolve("main.on"), "IO::println(\"hi2\")\n", UTF_8)
    val result = invoke(project)

    withClue(s"stderr was: ${result.stderr}") { result.exitCode shouldBe 0 }
    Files.readString(project.resolve("onion.lock"), UTF_8) shouldBe first

  test("a lock recording bytes that are no longer there stops the build"):
    val project = fixture(FixtureMavenRepository.publish(), "IO::println(\"hi\")\n")
    invoke(project).exitCode shouldBe 0

    // Stand in for a repository that re-published a version: the lock now disagrees with
    // what resolution produces. Compiling anyway would mean not knowing what it compiled
    // against.
    val tampered = Files.readString(project.resolve("onion.lock"), UTF_8)
      .replaceAll("""sha256 = "[0-9a-f]{64}"""", """sha256 = "%s"""".format("0" * 64))
    Files.writeString(project.resolve("onion.lock"), tampered, UTF_8)
    Files.writeString(project.resolve("src").resolve("main.on"), "IO::println(\"hi3\")\n", UTF_8)

    val result = invoke(project)

    result.exitCode should not be 0
    result.stderr should include("onion.lock")

  test("changing a declared version discards the lock instead of half-honouring it"):
    val repository = FixtureMavenRepository.publish()
    val project = fixture(repository, "IO::println(\"hi\")\n")
    invoke(project).exitCode shouldBe 0

    // A lock that answers a different question is not an answer: it is replaced, not
    // enforced against a manifest it no longer describes.
    val stale = Files.readString(project.resolve("onion.lock"), UTF_8)
      .replace(s"${FixtureMavenRepository.Group}:greeter:1.0.0", s"${FixtureMavenRepository.Group}:greeter:0.0.1")
    Files.writeString(project.resolve("onion.lock"), stale, UTF_8)
    Files.writeString(project.resolve("src").resolve("main.on"), "IO::println(\"hi4\")\n", UTF_8)

    val result = invoke(project)

    withClue(s"stderr was: ${result.stderr}") { result.exitCode shouldBe 0 }
    Files.readString(project.resolve("onion.lock"), UTF_8) should include(
      s"${FixtureMavenRepository.Group}:greeter:1.0.0")

  // ------------------------------------------------------------------ fixture plumbing

  private def fixture(repository: Path, source: String): Path =
    val root = Files.createTempDirectory("onion-project-dependency").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"),
      s"""[package]
         |name = "demo"
         |version = "1.0.0"
         |
         |${FixtureMavenRepository.manifestStanzas(repository)}""".stripMargin,
      UTF_8
    )
    val main = root.resolve("src").resolve("main.on")
    Files.createDirectories(main.getParent)
    Files.writeString(main, source, UTF_8)
    root

  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  private def invoke(root: Path): Invocation =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, UTF_8)
    val err = PrintStream(stderr, true, UTF_8)
    val exitCode =
      try ProjectCommands().run(root, verbose = false, Array.empty, out, err)
      finally
        out.close()
        err.close()
    Invocation(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8))
