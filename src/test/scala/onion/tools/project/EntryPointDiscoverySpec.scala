package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path

import onion.compiler.CompilerConfig
import onion.compiler.FileInputSource
import onion.compiler.Parsing
import onion.compiler.AST
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class EntryPointDiscoverySpec extends AnyFunSuite with Matchers:
  private val parserConfig =
    CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def parse(root: Path, sources: (String, String)*): Seq[AST.CompilationUnit] =
    val files = sources.map { case (relative, contents) =>
      val file = root.resolve(relative)
      Files.createDirectories(file.getParent)
      Files.writeString(file, contents)
      file
    }
    new Parsing(parserConfig).process(files.map(file => new FileInputSource(file.toString)))

  test("discovers executable src/main.on by convention"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(root, "src/main.on" -> """IO::println("hello")""")

    EntryPointDiscovery.discover(root, units).toOption.value shouldBe
      Vector(EntryPoint("mainMain", "src/main.on", 1, 3))

  test("does not treat declaration-only src/main.on as executable"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(root, "src/main.on" -> "class Helper {}")

    EntryPointDiscovery.discover(root, units).toOption.value shouldBe Vector.empty

  test("discovers an explicit top-level main and retains its location"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(
      root,
      "src/runner.on" ->
        """class Helper {}
          |
          |  def main(): void {}
          |""".stripMargin
    )

    EntryPointDiscovery.discover(root, units).toOption.value shouldBe
      Vector(EntryPoint("runnerMain", "src/runner.on", 3, 3))

  test("explicit main wins when src/main.on also satisfies the convention"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(
      root,
      "src/main.on" ->
        """IO::println("setup")
          |
          |  def main(): void {}
          |""".stripMargin
    )

    EntryPointDiscovery.discover(root, units).toOption.value shouldBe
      Vector(EntryPoint("mainMain", "src/main.on", 3, 3))

  test("ignores helper functions types extensions and globals"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(
      root,
      "src/helpers.on" ->
        """val answer: Int = 42
          |def helper(): Int = answer
          |class Helper {}
          |extension String {
          |  def identity(): String = self
          |}
          |""".stripMargin
    )

    EntryPointDiscovery.discover(root, units).toOption.value shouldBe Vector.empty

  test("returns multiple candidates in deterministic source order"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val parsed = parse(
      root,
      "src/zeta.on" -> "def main(): void {}",
      "src/alpha.on" -> "def main(): void {}"
    )

    EntryPointDiscovery.discover(root, parsed.reverse).toOption.value shouldBe Vector(
      EntryPoint("alphaMain", "src/alpha.on", 1, 1),
      EntryPoint("zetaMain", "src/zeta.on", 1, 1)
    )

  test("prefixes the generated top-level class with the source module"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(
      root,
      "src/runner.on" ->
        """module demo.cli
          |def main(): void {}
          |""".stripMargin
    )

    EntryPointDiscovery.discover(root, units).toOption.value shouldBe
      Vector(EntryPoint("demo.cli.runnerMain", "src/runner.on", 2, 1))

  test("rejects generated top-level class collisions when only one unit is an entry"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val units = parse(
      root,
      "src/first/helper.on" -> "def main(): void {}",
      "src/second/helper.on" -> "class Second {}"
    )

    val error = EntryPointDiscovery.discover(root, units).swap.toOption.value

    error.message should include("helperMain")
    error.message should include("src/first/helper.on")
    error.message should include("src/second/helper.on")

  test("rejects a source symlink that escapes the canonical project root"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val outside = Files.createTempDirectory("entry-point-discovery-outside")
    val outsideUnit = parse(outside, "runner.on" -> "def main(): void {}").head
    val sourceDirectory = Files.createDirectories(root.resolve("src"))
    val linkedSource =
      Files.createSymbolicLink(sourceDirectory.resolve("runner.on"), outside.resolve("runner.on"))
    val unit = outsideUnit.copy(sourceFile = linkedSource.toString)

    val error = EntryPointDiscovery.discover(root, Seq(unit)).swap.toOption.value

    error.message should include(outside.resolve("runner.on").toRealPath().toString)

  test("derives a test entry directly even without executable block content"):
    val root = Files.createTempDirectory("entry-point-discovery")
    val unit = parse(
      root,
      "tests/helper_test.on" ->
        """module demo.tests
          |class HelperTest {}
          |""".stripMargin
    ).head

    EntryPointDiscovery.testEntry(root, unit).toOption.value shouldBe
      EntryPoint("demo.tests.helper_testMain", "tests/helper_test.on", 1, 1)
