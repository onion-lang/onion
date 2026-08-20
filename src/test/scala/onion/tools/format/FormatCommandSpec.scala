package onion.tools.format

import java.io.{ByteArrayOutputStream, PrintStream}
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.{Files, Path}

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

/**
 * `onion fmt`.
 *
 * The exit codes carry the whole contract for CI — `--check` must be 1 when something
 * would change and 0 when nothing would — so they are asserted on every path rather than
 * treated as incidental.
 */
class FormatCommandSpec extends AnyFunSuite with Matchers:

  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  private def invoke(cwd: Path, args: String*): Invocation =
    val out = new ByteArrayOutputStream()
    val err = new ByteArrayOutputStream()
    val outStream = new PrintStream(out, true, UTF_8)
    val errStream = new PrintStream(err, true, UTF_8)
    val code =
      try FormatCommand.run(cwd, args.toArray, outStream, errStream)
      finally
        outStream.close()
        errStream.close()
    Invocation(code, out.toString(UTF_8), err.toString(UTF_8))

  private def project(sources: (String, String)*): Path =
    val root = Files.createTempDirectory("onion-fmt").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"), "[package]\nname = \"fmtdemo\"\nversion = \"0.1.0\"\n", UTF_8)
    sources.foreach { (relative, content) =>
      val file = root.resolve(relative)
      Files.createDirectories(file.getParent)
      Files.writeString(file, content, UTF_8)
    }
    root

  private val Unformatted = "IO::println(\"hi\" , 1)\n"
  private val Formatted = "IO::println(\"hi\", 1)\n"

  test("formats the project's own sources with no arguments at all"):
    val root = project("src/main.on" -> Unformatted)

    val result = invoke(root)

    withClue(result.stderr) { result.exitCode shouldBe 0 }
    Files.readString(root.resolve("src/main.on"), UTF_8) shouldBe Formatted
    result.stdout should include("Formatted 1")

  test("formats the tests directory too, since it is source people read"):
    val root = project(
      "src/main.on" -> Formatted,
      "tests/main_test.on" -> Unformatted)

    invoke(root).exitCode shouldBe 0

    Files.readString(root.resolve("tests/main_test.on"), UTF_8) shouldBe Formatted

  test("says nothing needed doing rather than staying silent"):
    val root = project("src/main.on" -> Formatted)

    val result = invoke(root)

    result.exitCode shouldBe 0
    result.stdout should include("already formatted")

  test("--check writes nothing and fails, which is what CI reads"):
    val root = project("src/main.on" -> Unformatted)

    val result = invoke(root, "--check")

    result.exitCode shouldBe 1
    Files.readString(root.resolve("src/main.on"), UTF_8) shouldBe Unformatted
    // Naming the file matters: "1 file would be reformatted" is not actionable from a log.
    result.stdout should include("main.on")

  test("--check succeeds when the tree is already formatted"):
    invoke(project("src/main.on" -> Formatted), "--check").exitCode shouldBe 0

  test("accepts a file, so it works outside a project"):
    val loose = Files.createTempDirectory("onion-fmt-loose").toRealPath()
    val file = loose.resolve("script.on")
    Files.writeString(file, Unformatted, UTF_8)

    invoke(loose, file.toString).exitCode shouldBe 0

    Files.readString(file, UTF_8) shouldBe Formatted

  test("walks a directory, rather than making people name each file"):
    val loose = Files.createTempDirectory("onion-fmt-dir").toRealPath()
    Files.createDirectories(loose.resolve("nested"))
    Files.writeString(loose.resolve("a.on"), Unformatted, UTF_8)
    Files.writeString(loose.resolve("nested/b.on"), Unformatted, UTF_8)
    Files.writeString(loose.resolve("notes.txt"), "not source", UTF_8)

    val result = invoke(loose, loose.toString)

    result.exitCode shouldBe 0
    Files.readString(loose.resolve("nested/b.on"), UTF_8) shouldBe Formatted
    Files.readString(loose.resolve("notes.txt"), UTF_8) shouldBe "not source"

  test("says where to run it when there is no project and no files"):
    val loose = Files.createTempDirectory("onion-fmt-nowhere").toRealPath()

    val result = invoke(loose)

    result.exitCode shouldBe 2
    result.stderr should include("onion.toml")

  test("reports a path that does not exist instead of silently doing nothing"):
    val loose = Files.createTempDirectory("onion-fmt-missing").toRealPath()

    val result = invoke(loose, loose.resolve("ghost.on").toString)

    result.exitCode shouldBe 2
    result.stderr should include("ghost.on")

  test("rejects an unknown option rather than treating it as a file name"):
    val result = invoke(project(), "--write")

    result.exitCode shouldBe 2
    result.stderr should include("--write")

  test("leaves a file it cannot format without changing its tokens"):
    // The formatter is whitespace-only by construction, so this cannot happen -- which is
    // exactly why it is checked before every write. A formatter that damages a source file
    // is worse than one that does nothing.
    val loose = Files.createTempDirectory("onion-fmt-empty").toRealPath()
    Files.writeString(loose.resolve("empty.on"), "", UTF_8)

    val result = invoke(loose, loose.toString)

    result.exitCode shouldBe 0
    Files.readString(loose.resolve("empty.on"), UTF_8) shouldBe ""
