package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectLocatorSpec extends AnyFunSuite with Matchers:
  private def projectDirectory(): Path =
    Files.createTempDirectory("onion-project-locator")

  private def manifest(directory: Path): Path =
    val path = directory.resolve("onion.toml")
    Files.writeString(path, "[package]\nname = \"example\"\nversion = \"1.0.0\"\n")
    path

  test("finds a manifest in the starting directory and derives conventional paths"):
    val root = projectDirectory()
    val manifestPath = manifest(root)

    val paths = ProjectLocator.locate(root).toOption.value

    paths.root shouldBe root.toRealPath()
    paths.manifest shouldBe manifestPath
    paths.target shouldBe paths.root.resolve("target")
    paths.classes shouldBe paths.target.resolve("classes")
    paths.onionState shouldBe paths.target.resolve(".onion")
    paths.buildState shouldBe paths.onionState.resolve("build-state.json")

  test("walks upward to find a manifest"):
    val root = projectDirectory()
    manifest(root)
    val nested = Files.createDirectories(root.resolve("one/two"))

    ProjectLocator.locate(nested).toOption.value.root shouldBe root.toRealPath()

  test("uses the nearest nested manifest"):
    val root = projectDirectory()
    manifest(root)
    val nestedProject = Files.createDirectories(root.resolve("nested"))
    manifest(nestedProject)
    val start = Files.createDirectories(nestedProject.resolve("child"))

    ProjectLocator.locate(start).toOption.value.root shouldBe nestedProject.toRealPath()

  test("accepts a relative starting path"):
    val root = projectDirectory()
    manifest(root)
    val relative = Paths.get("").toAbsolutePath().normalize().relativize(root)

    ProjectLocator.locate(relative).toOption.value.root shouldBe root.toRealPath()

  test("canonicalizes a symbolic-link starting path"):
    val root = projectDirectory()
    manifest(root)
    val link = root.getParent.resolve(root.getFileName.toString + "-link")
    Files.createSymbolicLink(link, root)

    ProjectLocator.locate(link).toOption.value.root shouldBe root.toRealPath()

  test("reports an absolute normalized starting path when no manifest exists"):
    val start = Files.createTempDirectory("onion-project-missing").resolve("missing/child")
    val expected = start.toAbsolutePath.normalize

    ProjectLocator.locate(start).left.toOption.value.message should include (expected.toString)
