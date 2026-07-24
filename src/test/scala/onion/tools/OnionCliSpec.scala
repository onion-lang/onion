package onion.tools

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import scala.collection.mutable.ArrayBuffer

import onion.tools.project.ProjectCommands
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class OnionCliSpec extends AnyFunSuite with Matchers:
  private enum ProjectCall:
    case Create(name: String, cwd: Path)
    case Build(cwd: Path, verbose: Boolean)
    case Run(cwd: Path, verbose: Boolean, args: Vector[String])
    case Test(cwd: Path, verbose: Boolean)
    case Clean(cwd: Path)

  private final class FakeLegacy(
    scriptResult: Int = 0,
    replResult: Int = 0
  ) extends LegacyCommands:
    val scriptCalls = ArrayBuffer.empty[Vector[String]]
    val replCalls = ArrayBuffer.empty[Vector[String]]

    override def script(args: Array[String]): Int =
      scriptCalls += args.toVector
      scriptResult

    override def repl(args: Array[String]): Int =
      replCalls += args.toVector
      replResult

  private final class FakeProjects(result: Int = 0) extends ProjectCommands:
    val calls = ArrayBuffer.empty[ProjectCall]

    override def create(name: String, cwd: Path, out: PrintStream, err: PrintStream): Int =
      calls += ProjectCall.Create(name, cwd)
      result

    override def build(cwd: Path, verbose: Boolean, out: PrintStream, err: PrintStream): Int =
      calls += ProjectCall.Build(cwd, verbose)
      result

    override def run(
      cwd: Path,
      verbose: Boolean,
      args: Array[String],
      out: PrintStream,
      err: PrintStream
    ): Int =
      calls += ProjectCall.Run(cwd, verbose, args.toVector)
      result

    override def test(cwd: Path, verbose: Boolean, out: PrintStream, err: PrintStream): Int =
      calls += ProjectCall.Test(cwd, verbose)
      result

    override def clean(cwd: Path, out: PrintStream, err: PrintStream): Int =
      calls += ProjectCall.Clean(cwd)
      result

  private final case class Invocation(
    exitCode: Int,
    stdout: String,
    stderr: String,
    legacy: FakeLegacy,
    projects: FakeProjects
  )

  private def invoke(
    args: Seq[String],
    cwd: Path = Files.createTempDirectory("onion-cli"),
    legacy: FakeLegacy = FakeLegacy(),
    projects: FakeProjects = FakeProjects()
  ): Invocation =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, StandardCharsets.UTF_8)
    val err = PrintStream(stderr, true, StandardCharsets.UTF_8)

    val exitCode = OnionCli.run(args.toArray, cwd, out, err, legacy, projects)

    Invocation(
      exitCode,
      stdout.toString(StandardCharsets.UTF_8),
      stderr.toString(StandardCharsets.UTF_8),
      legacy,
      projects
    )

  test("no arguments report unified project usage on stderr"):
    val result = invoke(Seq.empty)

    result.exitCode shouldBe 2
    result.stdout shouldBe empty
    result.stderr should include("Usage:")
    result.stderr should include("onion new <name>")
    result.stderr should include("onion run [--verbose] [-- <arguments...>]")
    result.legacy.scriptCalls shouldBe empty
    result.legacy.replCalls shouldBe empty
    result.projects.calls shouldBe empty

  test("project command words are reserved when they are the exact first argument"):
    val cases = Seq(
      "new" -> 2,
      "build" -> 0,
      "run" -> 0,
      "test" -> 0,
      "clean" -> 0
    )

    cases.foreach { (command, expectedExit) =>
      val result = invoke(Seq(command))

      withClue(command) {
        result.exitCode shouldBe expectedExit
        result.legacy.scriptCalls shouldBe empty
      }
    }

  test("near matches and leading script-runner options delegate unchanged"):
    val cases = Seq(
      Seq("new.on", "build"),
      Seq("builder", "run"),
      Seq("--run", "clean"),
      Seq("--help"),
      Seq("--version"),
      Seq("--verbose", "build", "--flag")
    )

    cases.foreach { args =>
      val result = invoke(args)

      result.exitCode shouldBe 0
      result.legacy.scriptCalls.toSeq shouldBe Seq(args.toVector)
      result.projects.calls shouldBe empty
    }

  test("repl delegates all remaining arguments and returns its exit code"):
    val legacy = FakeLegacy(replResult = 17)

    val result = invoke(Seq("repl", "--history", "shared"), legacy = legacy)

    result.exitCode shouldBe 17
    result.legacy.replCalls.toSeq shouldBe Seq(Vector("--history", "shared"))
    result.legacy.scriptCalls shouldBe empty
    result.projects.calls shouldBe empty

  test("a script path delegates unchanged and returns the legacy exit code"):
    val legacy = FakeLegacy(scriptResult = -1)
    val args = Seq("sample.on", "new", "--verbose")

    val result = invoke(args, legacy = legacy)

    result.exitCode shouldBe -1
    result.legacy.scriptCalls.toSeq shouldBe Seq(args.toVector)
    result.legacy.replCalls shouldBe empty

  test("new creates a project and reports success only on stdout"):
    val cwd = Files.createTempDirectory("onion-cli-new")
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    val exitCode = OnionCli.run(
      Array("new", "hello"),
      cwd,
      PrintStream(stdout, true, StandardCharsets.UTF_8),
      PrintStream(stderr, true, StandardCharsets.UTF_8)
    )

    exitCode shouldBe 0
    Files.isRegularFile(cwd.resolve("hello/onion.toml")) shouldBe true
    stdout.toString(StandardCharsets.UTF_8) should include("hello")
    stderr.toString(StandardCharsets.UTF_8) shouldBe empty

  test("new rejects missing and extra names as usage errors"):
    Seq(
      Seq("new"),
      Seq("new", "one", "two")
    ).foreach { args =>
      val result = invoke(args)

      withClue(args.mkString(" ")) {
        result.exitCode shouldBe 2
        result.stdout shouldBe empty
        result.stderr should include("Usage:")
        result.stderr should include("onion new <name>")
        result.projects.calls shouldBe empty
      }
    }

  test("new reports scaffold failures as project failures on stderr"):
    val cwd = Files.createTempDirectory("onion-cli-existing")
    Files.createDirectory(cwd.resolve("hello"))
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    val exitCode = OnionCli.run(
      Array("new", "hello"),
      cwd,
      PrintStream(stdout, true, StandardCharsets.UTF_8),
      PrintStream(stderr, true, StandardCharsets.UTF_8)
    )

    exitCode shouldBe 1
    stdout.toString(StandardCharsets.UTF_8) shouldBe empty
    stderr.toString(StandardCharsets.UTF_8) should include("error:")

  test("build and test accept one optional verbose flag"):
    val buildCwd = Files.createTempDirectory("onion-cli-build")
    val testCwd = Files.createTempDirectory("onion-cli-test")
    val build = invoke(Seq("build", "--verbose"), cwd = buildCwd)
    val test = invoke(Seq("test"), cwd = testCwd)

    build.exitCode shouldBe 0
    build.projects.calls.toSeq shouldBe Seq(ProjectCall.Build(buildCwd, true))
    test.exitCode shouldBe 0
    test.projects.calls.toSeq shouldBe Seq(ProjectCall.Test(testCwd, false))

  test("run accepts verbose before the separator and passes later arguments verbatim"):
    val cwd = Files.createTempDirectory("onion-cli-run")

    val result = invoke(
      Seq("run", "--verbose", "--", "--verbose", "new", "", "--"),
      cwd = cwd
    )

    result.exitCode shouldBe 0
    result.projects.calls.toSeq shouldBe Seq(
      ProjectCall.Run(cwd, verbose = true, Vector("--verbose", "new", "", "--"))
    )

  test("run accepts an empty separator and defaults verbose to false"):
    val cwd = Files.createTempDirectory("onion-cli-run-empty")

    val result = invoke(Seq("run", "--"), cwd = cwd)

    result.exitCode shouldBe 0
    result.projects.calls.toSeq shouldBe Seq(ProjectCall.Run(cwd, verbose = false, Vector.empty))

  test("run rejects arguments before the separator as usage errors"):
    Seq(
      Seq("run", "argument"),
      Seq("run", "--verbose", "argument"),
      Seq("run", "--verbose", "--verbose"),
      Seq("run", "--unknown")
    ).foreach { args =>
      val result = invoke(args)

      withClue(args.mkString(" ")) {
        result.exitCode shouldBe 2
        result.stdout shouldBe empty
        result.stderr should include("Usage:")
        result.stderr should include("onion run [--verbose] [-- <arguments...>]")
        result.projects.calls shouldBe empty
      }
    }

  test("other project commands reject unknown options and misplaced arguments"):
    Seq(
      Seq("new", "--verbose"),
      Seq("build", "--unknown"),
      Seq("build", "--verbose", "--verbose"),
      Seq("test", "--"),
      Seq("clean", "--verbose")
    ).foreach { args =>
      val result = invoke(args)

      withClue(args.mkString(" ")) {
        result.exitCode shouldBe 2
        result.stdout shouldBe empty
        result.stderr should include("error:")
        result.stderr should include("Usage:")
        result.projects.calls shouldBe empty
      }
    }

  test("the remaining temporary clean implementation uses stderr and exits one"):
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    val exitCode = OnionCli.run(
      Array("clean"),
      Files.createTempDirectory("onion-cli-clean"),
      PrintStream(stdout, true, StandardCharsets.UTF_8),
      PrintStream(stderr, true, StandardCharsets.UTF_8)
    )

    exitCode shouldBe 1
    stdout.toString(StandardCharsets.UTF_8) shouldBe empty
    stderr.toString(StandardCharsets.UTF_8) should include("onion clean is not implemented")

  test("ScriptRunner runMain preserves help, version, watch usage, and empty-invocation exit behavior"):
    val stdout = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, StandardCharsets.UTF_8)

    val helpExit = Console.withOut(out) {
      ScriptRunner.runMain(Array("--help"))
    }
    val versionExit = Console.withOut(out) {
      ScriptRunner.runMain(Array("--version"))
    }
    val emptyExit = Console.withOut(out) {
      ScriptRunner.runMain(Array.empty[String])
    }
    val watchExit = Console.withOut(out) {
      ScriptRunner.runMain(Array("--watch"))
    }

    helpExit shouldBe 0
    versionExit shouldBe 0
    emptyExit shouldBe -1
    watchExit shouldBe 0
    stdout.toString(StandardCharsets.UTF_8) should include("Usage: onion [options] <source_file>")
    stdout.toString(StandardCharsets.UTF_8) should include(s"Onion Script Runner version ${ScriptRunner.VERSION}")

  test("ScriptRunner runMain unwraps exceptions thrown by a script"):
    val source = Files.createTempFile("onion-cli-throws", ".on")
    Files.writeString(
      source,
      """class Explodes {
        |public:
        |  static def main(args: String[]): void {
        |    throw new RuntimeException("boom");
        |  }
        |}
        |""".stripMargin,
      StandardCharsets.UTF_8
    )

    val thrown = intercept[RuntimeException] {
      ScriptRunner.runMain(Array("--verbose", "--Wno", "unused-parameter", source.toString))
    }

    thrown.getMessage shouldBe "boom"
