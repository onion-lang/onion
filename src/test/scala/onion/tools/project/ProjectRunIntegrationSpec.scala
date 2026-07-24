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
      "error: Project has no entrypoint; add executable top-level code to src/main.on or define a top-level main function\n"
    )

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
    val zero = numericEntrypointFixture(0)
    val nonzero = numericEntrypointFixture(23)
    val tinyNonzero = cachedEntrypointFixture(
      """class NumericEntrypoint {
        |public:
        |  static def main(args: String[]): java.math.BigDecimal {
        |    return new java.math.BigDecimal("1e-10000")
        |  }
        |}
        |def main(): void {}
        |""".stripMargin
    )

    invoke(zero.root) shouldBe Invocation(0, "", "")
    invoke(nonzero.root) shouldBe Invocation(1, "", "")
    invoke(tinyNonzero.root) shouldBe Invocation(1, "", "")

  private def numericEntrypointFixture(value: Int): Fixture =
    cachedEntrypointFixture(
      s"""class NumericEntrypoint {
         |public:
         |  static def main(args: String[]): Int {
         |    return args.length * 0 + $value
         |  }
         |}
         |def main(): void {}
         |""".stripMargin
    )

  private def cachedEntrypointFixture(source: String): Fixture =
    val project = fixture(Map("src/main.on" -> source))
    invoke(project.root).exitCode shouldBe 0
    val state = BuildState.load(project.paths.buildState).toOption.value
    state.classes should contain("NumericEntrypoint")
    BuildState.write(
      project.paths.buildState,
      state.copy(entryPoints = Vector(
        EntryPoint("NumericEntrypoint", "src/main.on", 3, 3)
      ))
    ).toOption.value
    project

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

  private def propertyKey(label: String): String =
    s"onion.project.run.$label.${java.util.UUID.randomUUID()}"
