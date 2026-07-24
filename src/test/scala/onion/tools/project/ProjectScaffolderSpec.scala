package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ProjectScaffolderSpec extends AnyFunSuite with Matchers:
  private def parentDirectory(): Path =
    Files.createTempDirectory("onion-project-scaffolder")

  test("creates the conventional project files with exact UTF-8 contents"):
    val parent = parentDirectory()

    val paths = ProjectScaffolder.create(parent, "hello").toOption.value

    paths.root shouldBe parent.resolve("hello").toRealPath()
    String(Files.readAllBytes(paths.manifest), StandardCharsets.UTF_8) shouldBe
      "[package]\nname = \"hello\"\nversion = \"0.1.0\"\n"
    String(Files.readAllBytes(paths.root.resolve("src/main.on")), StandardCharsets.UTF_8) shouldBe
      "def main(): void {\n  println(\"Hello, hello!\")\n}\n"
    String(Files.readAllBytes(paths.root.resolve("tests/main_test.on")), StandardCharsets.UTF_8) shouldBe
      "Assert::equals(4, 2 + 2)\n"

  test("rejects an invalid name without creating a project path"):
    val parent = parentDirectory()

    ProjectScaffolder.create(parent, "not/a-project").isLeft shouldBe true

    Files.notExists(parent.resolve("not")) shouldBe true

  test("does not overwrite an existing file"):
    val parent = parentDirectory()
    val existing = parent.resolve("hello")
    Files.writeString(existing, "keep this")

    ProjectScaffolder.create(parent, "hello").isLeft shouldBe true

    Files.readString(existing) shouldBe "keep this"

  test("does not overwrite an existing directory"):
    val parent = parentDirectory()
    val existing = Files.createDirectory(parent.resolve("hello"))
    val sentinel = existing.resolve("sentinel")
    Files.writeString(sentinel, "keep this")

    ProjectScaffolder.create(parent, "hello").isLeft shouldBe true

    Files.readString(sentinel) shouldBe "keep this"

  test("cleans up a root created by an invocation that later fails"):
    val parent = parentDirectory()
    val root = parent.resolve("hello")

    val result = ProjectScaffolder.create(parent, "hello", path =>
      if path.getFileName.toString == "main.on" then throw IOException("write failed")
      else Files.writeString(path, "created", StandardCharsets.UTF_8)
    )

    result.isLeft shouldBe true
    Files.notExists(root) shouldBe true

  test("does not create a Git repository"):
    val parent = parentDirectory()
    val paths = ProjectScaffolder.create(parent, "hello").toOption.value

    Files.notExists(paths.root.resolve(".git")) shouldBe true
