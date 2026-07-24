package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes

object ProjectScaffolder:
  private val ManifestContents =
    "[package]\nname = \"%s\"\nversion = \"0.1.0\"\n"

  private val MainContents =
    "def main(args: String[]): void {\n  println(\"Hello, %s!\")\n}\n"

  private val TestContents = "Assert::assertEquals(4, 2 + 2)\n"

  def create(parent: Path, name: String): Either[ProjectError, ProjectPaths] =
    create(parent, name, path => write(path, contents(path, name)))

  private[project] def create(parent: Path, name: String, writeFile: Path => Unit): Either[ProjectError, ProjectPaths] =
    if !ProjectManifest.validName(name) then
      Left(ProjectError(s"Invalid project name: $name"))
    else
      val root = parent.toAbsolutePath.normalize.resolve(name)
      var createdRoot = false
      try
        Files.createDirectory(root)
        createdRoot = true
        Files.createDirectory(root.resolve("src"))
        Files.createDirectory(root.resolve("tests"))
        writeFile(root.resolve("onion.toml"))
        writeFile(root.resolve("src/main.on"))
        writeFile(root.resolve("tests/main_test.on"))
        Right(paths(root.toRealPath()))
      catch
        case error: Exception =>
          if createdRoot then
            try deleteTree(root)
            catch case _: IOException => ()
          Left(ProjectError(s"Could not create project $name: ${error.getMessage}", Some(error)))

  private def contents(path: Path, name: String): String =
    path.getFileName.toString match
      case "onion.toml" => ManifestContents.format(name)
      case "main.on" => MainContents.format(name)
      case "main_test.on" => TestContents

  private def write(path: Path, contents: String): Unit =
    Files.writeString(path, contents, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)

  private def paths(root: Path): ProjectPaths =
    val target = root.resolve("target")
    val onionState = target.resolve(".onion")
    ProjectPaths(root, root.resolve("onion.toml"), target, target.resolve("classes"), onionState, onionState.resolve("build-state.json"))

  private def deleteTree(root: Path): Unit =
    Files.walkFileTree(root, new SimpleFileVisitor[Path]:
      override def visitFile(path: Path, attributes: BasicFileAttributes): FileVisitResult =
        Files.delete(path)
        FileVisitResult.CONTINUE

      override def postVisitDirectory(path: Path, error: IOException | Null): FileVisitResult =
        if error != null then throw error
        Files.delete(path)
        FileVisitResult.CONTINUE
    )
