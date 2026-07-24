package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectLayoutSpec extends AnyFunSuite with Matchers:
  private def paths(root: Path): ProjectPaths =
    val target = root.resolve("target")
    val onionState = target.resolve(".onion")
    ProjectPaths(root, root.resolve("onion.toml"), target, target.resolve("classes"), onionState, onionState.resolve("build-state.json"))

  private def write(root: Path, relative: String): Path =
    val path = root.resolve(relative)
    Files.createDirectories(path.getParent)
    Files.writeString(path, "// fixture\n")
    path

  test("discovers recursive sorted production Onion sources and ignores non-Onion files"):
    val root = Files.createTempDirectory("onion-project-layout")
    write(root, "src/zeta.on")
    write(root, "src/alpha.on")
    write(root, "src/nested/beta.on")
    write(root, "src/nested/ignored.txt")
    write(root, "src/helper_test.on")

    val layout = ProjectLayout.discover(paths(root)).toOption.value

    layout.productionSources.map(_.relative) shouldBe Vector("src/alpha.on", "src/helper_test.on", "src/nested/beta.on", "src/zeta.on")
    layout.productionSources.map(_.path) shouldBe Vector(
      root.resolve("src/alpha.on"),
      root.resolve("src/helper_test.on"),
      root.resolve("src/nested/beta.on"),
      root.resolve("src/zeta.on")
    )

  test("discovers only recursive sorted *_test.on test sources"):
    val root = Files.createTempDirectory("onion-project-layout")
    write(root, "tests/z_test.on")
    write(root, "tests/nested/a_test.on")
    write(root, "tests/not_a_test.on.bak")
    write(root, "tests/helper.on")
    write(root, "tests/readme.md")

    ProjectLayout.discover(paths(root)).toOption.value.testSources.map(_.relative) shouldBe
      Vector("tests/nested/a_test.on", "tests/z_test.on")

  test("returns empty production sources when src is missing"):
    val root = Files.createTempDirectory("onion-project-layout")

    ProjectLayout.discover(paths(root)).toOption.value.productionSources shouldBe Vector.empty

  test("returns empty test sources when tests is missing"):
    val root = Files.createTempDirectory("onion-project-layout")

    ProjectLayout.discover(paths(root)).toOption.value.testSources shouldBe Vector.empty

  test("normalizes source relative paths with forward slashes"):
    val root = Files.createTempDirectory("onion-project-layout")
    write(root, "src/nested/example.on")

    ProjectLayout.discover(paths(root)).toOption.value.productionSources.head.relative shouldBe "src/nested/example.on"

  test("ignores directory and file symbolic links"):
    val root = Files.createTempDirectory("onion-project-layout")
    write(root, "src/kept.on")
    val external = Files.createTempDirectory("onion-project-layout-external")
    val linkedFile = write(external, "linked.on")
    write(external, "nested/inside.on")
    Files.createSymbolicLink(root.resolve("src/file-link.on"), linkedFile)
    Files.createSymbolicLink(root.resolve("src/directory-link"), external)

    val layout = ProjectLayout.discover(paths(root)).toOption.value

    layout.productionSources.map(_.relative) shouldBe Vector("src/kept.on")
