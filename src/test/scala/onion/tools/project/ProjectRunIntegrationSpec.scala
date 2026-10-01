package onion.tools.project

import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.attribute.FileTime
import java.nio.file.Files
import java.nio.file.Path

import onion.tools.OnionCli
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectRunIntegrationSpec extends AnyFunSuite with Matchers:
  private final case class Fixture(root: Path, paths: ProjectPaths)
  private final case class Invocation(exitCode: Int, stdout: String, stderr: String)

  test("builds then executes the sole source-discovered entrypoint"):
    val key = propertyKey("build")
    val project = fixture(
      Map(
        "src/main.on" ->
          s"""def main(args: String[]): void {
             |  System::setProperty("$key", args[0])
             |}
             |""".stripMargin
      )
    )

    try
      Files.notExists(project.paths.target) shouldBe true

      val result = invoke(project.root, args = Array("executed"))

      result shouldBe Invocation(0, "", "")
      System.getProperty(key) shouldBe "executed"
      Files.isDirectory(project.paths.classes) shouldBe true
      Files.isRegularFile(project.paths.buildState) shouldBe true
    finally System.clearProperty(key)

  test("a cached build still executes the program"):
    val key = propertyKey("cached")
    val project = fixture(
      Map(
        "src/main.on" ->
          s"""def main(args: String[]): void {
             |  System::setProperty("$key", args[0])
             |}
             |""".stripMargin
      )
    )

    try
      invoke(project.root, args = Array("first")).exitCode shouldBe 0
      System.getProperty(key) shouldBe "first"
      val preservedTime = FileTime.fromMillis(1000)
      Files.setLastModifiedTime(project.paths.buildState, preservedTime)

      val cached = invoke(project.root, args = Array("second"))

      cached shouldBe Invocation(0, "", "")
      System.getProperty(key) shouldBe "second"
      Files.getLastModifiedTime(project.paths.buildState) shouldBe preservedTime
    finally System.clearProperty(key)

  test("reports how to create an entrypoint when none is discovered"):
    val project = fixture(Map("src/helper.on" -> "class Helper {}\n"))

    invoke(project.root) shouldBe Invocation(
      1,
      "",
      "error: Project has no entrypoint; add executable top-level code to src/main.on, define a top-level main function, or declare a top-level tool\n"
    )

  test("a tool-only src/main.on runs through the synthesized tool CLI"):
    val key = propertyKey("tool")
    val project = fixture(
      Map(
        "src/main.on" ->
          s"""tool store(value: String): Int requires { env } {
             |  System::setProperty("$key", value)
             |  return 0
             |}
             |""".stripMargin
      )
    )

    try
      invoke(project.root, args = Array("ran")) shouldBe Invocation(0, "", "")
      System.getProperty(key) shouldBe "ran"
    finally System.clearProperty(key)

  test("onion run -- --help and -- --plan reach the tool CLI, named after the package"):
    val programNameBefore = System.getProperty("onion.cli.script")
    val project = fixture(
      Map(
        "src/main.on" ->
          """tool save(dst: String): Int requires { write(dst) } {
            |  Files::writeText(dst, "saved")
            |  return 0
            |}
            |""".stripMargin
      )
    )
    val target = project.root.resolve("out.txt")

    val (helpCode, help) = captureSystemOut(OnionCli.run(
      Array("run", "--", "--help"), project.root, System.out, System.err))
    helpCode shouldBe 0
    help should include("usage: demo <dst>")
    help should include("--plan")

    val (planCode, plan) = captureSystemOut(OnionCli.run(
      Array("run", "--", target.toString, "--plan"), project.root, System.out, System.err))
    planCode shouldBe 0
    plan should include("plan: `save` would")
    plan should include(s"derived from dst = $target")
    // --plan executes nothing.
    Files.exists(target) shouldBe false
    // The program name is restored once the run is over.
    System.getProperty("onion.cli.script") shouldBe programNameBefore

    val (runCode, _) = captureSystemOut(OnionCli.run(
      Array("run", "--", target.toString), project.root, System.out, System.err))
    runCode shouldBe 0
    Files.readString(target, UTF_8) shouldBe "saved"

  test("a tool source beside an explicit main is still ambiguous"):
    val project = fixture(
      Map(
        "src/cli.on" -> "tool ping(): Int { return 0 }\n",
        "src/main.on" -> "def main(): void {}\n"
      )
    )

    val result = invoke(project.root)
    result.exitCode shouldBe 1
    result.stderr should startWith("error: Project has multiple entrypoints:")
    result.stderr should include("src/cli.on:1:1 (cliMain)")
    result.stderr should include("src/main.on:1:1 (mainMain)")

  test("lists ambiguous entrypoints by normalized source path and location"):
    val project = fixture(
      Map(
        "src/zeta.on" -> "def main(): void {}\n",
        "src/alpha.on" -> "\n  def main(): void {}\n"
      )
    )

    invoke(project.root) shouldBe Invocation(
      1,
      "",
      """error: Project has multiple entrypoints:
        |  src/alpha.on:2:3 (alphaMain)
        |  src/zeta.on:1:1 (zetaMain)
        |""".stripMargin
    )

  test("discovers and runs a project from a nested working directory"):
    val key = propertyKey("nested")
    val project = fixture(
      Map(
        "src/main.on" ->
          s"""def main(args: String[]): void {
             |  System::setProperty("$key", args[0])
             |}
             |""".stripMargin
      )
    )
    val nested = Files.createDirectories(project.root.resolve("work/deep"))

    try
      invoke(nested, args = Array("nested")) shouldBe Invocation(0, "", "")
      System.getProperty(key) shouldBe "nested"
    finally System.clearProperty(key)

  test("the unified CLI forwards only arguments after the run separator verbatim"):
    val key = propertyKey("args")
    val project = fixture(
      Map(
        "src/main.on" ->
          s"""def main(args: String[]): void {
             |  System::setProperty("$key", args[0] + "|" + args[1] + "|" + args[2])
             |}
             |""".stripMargin
      )
    )
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()

    try
      val exitCode = OnionCli.run(
        Array("run", "--", "--verbose", "", "--"),
        project.root,
        PrintStream(stdout, true, UTF_8),
        PrintStream(stderr, true, UTF_8)
      )

      exitCode shouldBe 0
      stdout.toString(UTF_8) shouldBe empty
      stderr.toString(UTF_8) shouldBe empty
      System.getProperty(key) shouldBe "--verbose||--"
    finally System.clearProperty(key)

  test("prints a concise runtime error without a stack trace by default"):
    val project = throwingFixture()

    val result = invoke(project.root)

    result.exitCode shouldBe 1
    result.stdout shouldBe empty
    result.stderr shouldBe "error: IllegalStateException: boom\n"

  test("verbose runtime errors include the unwrapped user exception stack trace"):
    val project = throwingFixture()

    val result = invoke(project.root, verbose = true)

    result.exitCode shouldBe 1
    result.stdout shouldBe empty
    result.stderr should startWith(
      "error: IllegalStateException: boom\njava.lang.IllegalStateException: boom\n"
    )
    result.stderr should include("at ")
    result.stderr should include("main(")

  test("normalizes numeric program results to command exit zero or one"):
    val zero = fixture(
      Map(
        "src/main.on" ->
          """def main(args: String[]): Int {
            |  return args.length - args.length
            |}
            |""".stripMargin
      )
    )
    val nonzero = fixture(
      Map(
        "src/main.on" ->
          """def main(args: String[]): Int {
            |  return args.length + 23
            |}
            |""".stripMargin
      )
    )
    val tinyNonzero = fixture(
      Map(
        "src/main.on" ->
          """def main(args: String[]): java.math.BigDecimal {
            |  return new java.math.BigDecimal(
            |    (args.length + 1).toString() + "e-10000"
            |  )
            |}
            |""".stripMargin
      )
    )

    invoke(zero.root) shouldBe Invocation(0, "", "")
    invoke(nonzero.root) shouldBe Invocation(1, "", "")
    invoke(tinyNonzero.root) shouldBe Invocation(1, "", "")

  test("auto-CLI compatibility wrappers do not expose user return values as exit status"):
    val cases = Vector(
      fixture(Map(
        "src/main.on" ->
          """def main(): Int {
            |  return 23
            |}
            |""".stripMargin
      )) -> Array.empty[String],
      fixture(Map(
        "src/main.on" ->
          """def main(value: Int): Int {
            |  return value + 22
            |}
            |""".stripMargin
      )) -> Array("1")
    )

    // v1 runs zero-argument and scalar mains through the compiler's generated
    // void main(String[]) auto-CLI wrapper, so their user return value is not
    // a process exit status. Only a raw String[] main exposes its return value.
    cases.foreach { case (project, args) =>
      invoke(project.root, args = args) shouldBe Invocation(0, "", "")
    }

  private def throwingFixture(): Fixture =
    fixture(
      Map(
        "src/main.on" ->
          """def main(): void {
            |  throw new IllegalStateException("boom")
            |}
            |""".stripMargin
      )
    )

  private def fixture(sources: Map[String, String]): Fixture =
    val root = Files.createTempDirectory("onion-project-run").toRealPath()
    Files.writeString(
      root.resolve("onion.toml"),
      """[package]
        |name = "demo"
        |version = "1.0.0"
        |""".stripMargin,
      UTF_8
    )
    sources.foreach { case (relative, contents) =>
      val path = root.resolve(relative)
      Files.createDirectories(path.getParent)
      Files.writeString(path, contents, UTF_8)
    }
    Fixture(root, ProjectLocator.locate(root).toOption.value)

  private def invoke(
    cwd: Path,
    verbose: Boolean = false,
    args: Array[String] = Array.empty
  ): Invocation =
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val out = PrintStream(stdout, true, UTF_8)
    val err = PrintStream(stderr, true, UTF_8)
    val exitCode =
      try ProjectCommands().run(cwd, verbose, args, out, err)
      finally
        out.close()
        err.close()
    Invocation(exitCode, stdout.toString(UTF_8), stderr.toString(UTF_8))

  /** The tool CLI prints through `System.out`, not through the command's `out` stream. */
  private def captureSystemOut(body: => Int): (Int, String) =
    val buffer = ByteArrayOutputStream()
    val stream = PrintStream(buffer, true, UTF_8)
    val saved = System.out
    val code =
      try
        System.setOut(stream)
        Console.withOut(stream)(body)
      finally System.setOut(saved)
    (code, buffer.toString(UTF_8))

  private def propertyKey(label: String): String =
    s"onion.project.run.$label.${java.util.UUID.randomUUID()}"
