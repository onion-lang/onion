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
