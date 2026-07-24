package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectTestIntegrationSpec extends AnyFunSuite with Matchers:
  private final case class Fixture(root: Path, paths: ProjectPaths)
  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  test("passes an assertion, hides successful output, and shows it in verbose mode"):
    val project = fixture(
      tests = Map(
        "tests/pass_test.on" ->
          """println("passing-out")
            |IO::eprintln("passing-err")
            |Assert::isTrue(2 + 2 == 4)
            |""".stripMargin
      )
    )

    invoke(project.root) shouldBe Invocation(
      0,
      """test tests/pass_test.on ... ok
        |
        |1 tests, 1 passed, 0 failed
        |""".stripMargin,
      ""
    )

    invoke(project.root, verbose = true) shouldBe Invocation(
      0,
      """test tests/pass_test.on ... ok
        |passing-out
        |
        |1 tests, 1 passed, 0 failed
        |""".stripMargin,
      "passing-err\n"
    )
    temporaryTestDirectories(project.paths) shouldBe empty

  test("shows captured output and a concise assertion failure"):
    val project = fixture(
      tests = Map(
        "tests/assertion_test.on" ->
          """println("assertion-out")
            |IO::eprintln("assertion-err")
            |Assert::isTrue(false, "not true")
            |""".stripMargin
      )
    )

    val result = invoke(project.root)

    result.exitCode shouldBe 1
    result.stdout shouldBe
      """test tests/assertion_test.on ... FAILED
        |assertion-out
        |
        |1 tests, 0 passed, 1 failed
        |""".stripMargin
    result.stderr shouldBe
      """assertion-err
        |error: tests/assertion_test.on: AssertionError: not true
        |""".stripMargin
    temporaryTestDirectories(project.paths) shouldBe empty

  test("compile and runtime failures do not prevent later tests"):
    val project = fixture(
      tests = Map(
        "tests/a_compile_test.on" -> "missingName\n",
        "tests/b_runtime_test.on" ->
          """throw new IllegalStateException("runtime boom")
            |""".stripMargin,
        "tests/c_pass_test.on" ->
          """println("continued")
            |Assert::isTrue(true)
            |""".stripMargin
      )
    )

    val result = invoke(project.root, verbose = true)

    result.exitCode shouldBe 1
    result.stdout should include("test tests/a_compile_test.on ... FAILED\n")
    result.stdout should include("test tests/b_runtime_test.on ... FAILED\n")
    result.stdout should include("test tests/c_pass_test.on ... ok\ncontinued\n")
    result.stdout should endWith("\n3 tests, 1 passed, 2 failed\n")
    result.stderr should include("a_compile_test.on")
    result.stderr should include("error: tests/a_compile_test.on: Test compilation failed\n")
    result.stderr should include(
      "error: tests/b_runtime_test.on: IllegalStateException: runtime boom\n"
    )
    temporaryTestDirectories(project.paths) shouldBe empty

  test("a nonzero raw-argv main fails while an auto-CLI wrapper remains successful"):
    val project = fixture(
      tests = Map(
        "tests/a_nonzero_test.on" ->
          """def main(args: String[]): Int {
            |  return 7
            |}
            |""".stripMargin,
        "tests/b_auto_cli_test.on" ->
          """def main(): Int {
            |  return 7
            |}
            |""".stripMargin
      )
    )

    val result = invoke(project.root)

    result.exitCode shouldBe 1
    result.stdout shouldBe
      """test tests/a_nonzero_test.on ... FAILED
        |test tests/b_auto_cli_test.on ... ok
        |
        |2 tests, 1 passed, 1 failed
        |""".stripMargin
    result.stderr shouldBe
      """[W0006] tests/a_nonzero_test.on:1:10: warning: unused parameter 'args'
        |1 warning(s)
        |error: tests/a_nonzero_test.on: Test returned nonzero numeric result: 7
        |""".stripMargin

  test("loads production classes for every test with fresh static state"):
    val project = fixture(
      production =
        """class Counter {
          |private:
          |  static var count: Int = 0
          |public:
          |  static def next(): Int {
          |    Counter::count = Counter::count + 1
          |    return Counter::count
          |  }
          |}
          |
          |def main(): void {}
          |""".stripMargin,
      tests = Map(
        "tests/a_counter_test.on" -> "Assert::equals(1, Counter::next())\n",
        "tests/b_counter_test.on" -> "Assert::equals(1, Counter::next())\n"
      )
    )

    invoke(project.root) shouldBe Invocation(
      0,
      """test tests/a_counter_test.on ... ok
        |test tests/b_counter_test.on ... ok
        |
        |2 tests, 2 passed, 0 failed
        |""".stripMargin,
      ""
    )

  test("compiles each test source alone instead of sharing earlier test classes"):
    val project = fixture(
      tests = Map(
        "tests/a_defines_test.on" ->
          """class TestOnly {}
            |Assert::isTrue(true)
            |""".stripMargin,
        "tests/b_uses_test.on" -> "Assert::notNull(new TestOnly())\n"
      )
    )

    val result = invoke(project.root)

    result.exitCode shouldBe 1
    result.stdout shouldBe
      """test tests/a_defines_test.on ... ok
        |test tests/b_uses_test.on ... FAILED
        |
        |2 tests, 1 passed, 1 failed
        |""".stripMargin
    result.stderr should include("tests/b_uses_test.on")
    result.stderr should include("TestOnly")
    result.stderr should endWith(
      "error: tests/b_uses_test.on: Test compilation failed\n"
    )

  test("missing and empty test directories have the exact successful zero summary"):
    val missing = fixture(tests = Map.empty, createTestsDirectory = false)
    val empty = fixture(tests = Map.empty, createTestsDirectory = true)

    invoke(missing.root) shouldBe Invocation(
      0,
      "0 tests, 0 passed, 0 failed\n",
      ""
    )
    invoke(empty.root) shouldBe Invocation(
      0,
      "0 tests, 0 passed, 0 failed\n",
      ""
    )

  test("a cached production build still recompiles and executes changed tests"):
    val project = fixture(
      tests = Map("tests/cache_test.on" -> "println(\"first-test\")\n")
    )

    invoke(project.root, verbose = true).stdout should include("first-test\n")
    val preservedTime = FileTime.fromMillis(1000)
    Files.setLastModifiedTime(project.paths.buildState, preservedTime)
    Files.writeString(
      project.root.resolve("tests/cache_test.on"),
      "println(\"second-test\")\n",
      UTF_8
    )

    val second = invoke(project.root, verbose = true)

    second.exitCode shouldBe 0
    second.stdout should include("second-test\n")
    second.stdout should not include "first-test"
    Files.getLastModifiedTime(project.paths.buildState) shouldBe preservedTime
    temporaryTestDirectories(project.paths) shouldBe empty

  test("a production build failure prevents every test from executing"):
    val project = fixture(
      production = "missingProductionName\n",
      tests = Map("tests/would_run_test.on" -> "println(\"must-not-run\")\n")
    )

    val result = invoke(project.root, verbose = true)

    result.exitCode shouldBe 1
    result.stdout shouldBe empty
    result.stderr should include("Project compilation failed")
    result.stderr should not include "must-not-run"
    temporaryTestDirectories(project.paths) shouldBe empty

  test("rejects a symlinked state topology without touching its external target"):
    val project = fixture(
      tests = Map("tests/safe_test.on" -> "Assert::isTrue(true)\n")
    )
    invoke(project.root).exitCode shouldBe 0
    val build = loadBuild(project)
    val preserved = project.root.resolve("preserved-state")
    Files.move(project.paths.onionState, preserved)
    val external = Files.createTempDirectory("onion-project-test-external")
    val sentinel = Files.writeString(external.resolve("keep.txt"), "keep", UTF_8)
    Files.createSymbolicLink(project.paths.onionState, external)
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, UTF_8)
    val err = PrintStream(stderr, true, UTF_8)

    val result =
      try ProjectTestRunner().run(build, out, err, verbose = false)
      finally
        out.close()
        err.close()

    result.failed shouldBe 1
    result.cases.head.failure.value should include("symbolic")
    Files.readString(sentinel, UTF_8) shouldBe "keep"
    Using.resource(Files.list(external))(_.iterator.asScala.toVector.map(_.getFileName.toString)) shouldBe
      Vector("keep.txt")

  private def fixture(
    production: String = "def main(): void {}\n",
    tests: Map[String, String],
    createTestsDirectory: Boolean = true
  ): Fixture =
    val root = Files.createTempDirectory("onion-project-tests").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"),
      """[package]
        |name = "demo"
        |version = "1.0.0"
        |""".stripMargin,
      UTF_8
    )
    Files.createDirectory(root.resolve("src"))
    Files.writeString(root.resolve("src/main.on"), production, UTF_8)
    if createTestsDirectory then Files.createDirectory(root.resolve("tests"))
    tests.foreach { case (relative, contents) =>
      val path = root.resolve(relative)
      Files.createDirectories(path.getParent)
      Files.writeString(path, contents, UTF_8)
    }
    Fixture(root, ProjectLocator.locate(root).toOption.value)

  private def loadBuild(project: Fixture): ProjectBuild =
    val manifest = ProjectManifest.load(project.paths.manifest).toOption.value
    val layout = ProjectLayout.discover(project.paths).toOption.value
    val stderr = ByteArrayOutputStream()
    val err = PrintStream(stderr, true, UTF_8)
    try ProjectBuilder().build(project.paths, manifest, layout, err).toOption.value
    finally err.close()

  private def invoke(cwd: Path, verbose: Boolean = false): Invocation =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, UTF_8)
    val err = PrintStream(stderr, true, UTF_8)
    val exitCode =
      try ProjectCommands().test(cwd, verbose, out, err)
      finally
        out.close()
        err.close()
    Invocation(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8))

  private def temporaryTestDirectories(paths: ProjectPaths): Vector[Path] =
    if !Files.isDirectory(paths.onionState, NOFOLLOW_LINKS) then Vector.empty
    else
      Using.resource(Files.list(paths.onionState)) { stream =>
        stream.iterator.asScala
          .filter(path => path.getFileName.toString.startsWith("test-"))
          .toVector
      }
