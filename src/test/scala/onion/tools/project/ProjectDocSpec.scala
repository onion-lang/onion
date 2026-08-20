package onion.tools.project

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `onion doc`.
 *
 * `onion.tools.doc.OnionDoc` has been a complete generator for a while — six files, HTML
 * output, its own `-d` and `--help` — reachable from nothing: no launcher script, no
 * subcommand, no build task, no mention in any documentation. A feature nobody can invoke
 * is indistinguishable from one that does not exist, and nothing failed while that was
 * true, because there was no test that tried.
 */
class ProjectDocSpec extends AnyFunSuite with Matchers:

  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  private def invoke(cwd: Path, args: String*): Invocation =
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val outStream = new PrintStream(out, true, UTF_8)
    val errStream = new PrintStream(err, true, UTF_8)
    val code =
      try ProjectCommands().doc(cwd, args.toArray, outStream, errStream)
      finally
        outStream.close()
        errStream.close()
    Invocation(code, out.toString(UTF_8), err.toString(UTF_8))

  private def project(): Path =
    val root = Files.createTempDirectory("onion-doc").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"),
      "[package]\nname = \"docdemo\"\nversion = \"0.1.0\"\n",
      UTF_8)
    val main = root.resolve("src").resolve("main.on")
    Files.createDirectories(main.getParent)
    Files.writeString(
      main,
      """/**
        | * A greeter.
        | */
        |class Greeter {
        |public:
        |  /** Greets someone by name. */
        |  def greet(name: String): String = "hello " + name
        |}
        |""".stripMargin,
      UTF_8)
    root

  test("documents the project's own sources with no arguments at all"):
    val root = project()

    val result = invoke(root)

    withClue(result.stderr) { result.exitCode shouldBe 0 }
    val doc = root.resolve("target").resolve("doc")
    Files.isRegularFile(doc.resolve("index.html")) shouldBe true
    Files.isRegularFile(doc.resolve("Greeter.html")) shouldBe true

  test("carries the doc comments across, not just the signatures"):
    val root = project()
    invoke(root).exitCode shouldBe 0

    val html = Files.readString(root.resolve("target/doc/Greeter.html"), UTF_8)
    html should include("A greeter")
    html should include("Greets someone by name")

  test("honours an explicit output directory"):
    val root = project()

    invoke(root, "-d", root.resolve("api").toString).exitCode shouldBe 0

    Files.isRegularFile(root.resolve("api").resolve("index.html")) shouldBe true

  test("accepts explicit sources, so it works outside a project too"):
    val root = project()
    val loose = Files.createTempDirectory("onion-doc-loose").toRealPath()

    val result = invoke(
      loose, "-d", loose.resolve("out").toString, root.resolve("src/main.on").toString)

    withClue(result.stderr) { result.exitCode shouldBe 0 }
    Files.isRegularFile(loose.resolve("out").resolve("Greeter.html")) shouldBe true

  test("says where to run it when there is no project and no files"):
    val loose = Files.createTempDirectory("onion-doc-nowhere").toRealPath()

    val result = invoke(loose)

    result.exitCode shouldBe 2
    result.stderr should include("onion.toml")
    result.stderr should include("Name source files explicitly")

  test("rejects an unknown option rather than treating it as a file name"):
    val result = invoke(project(), "--html")

    result.exitCode shouldBe 2
    result.stderr should include("--html")

  test("reports a missing -d value instead of silently defaulting"):
    val result = invoke(project(), "-d")

    result.exitCode shouldBe 2
    result.stderr should include("-d requires")
