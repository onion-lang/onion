package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException

import org.tomlj.Toml
import org.tomlj.TomlParseError
import org.tomlj.TomlPosition
import org.tomlj.TomlTable

final case class ProjectManifest(
  name: String,
  version: String,
  path: Path,
  bytes: Array[Byte]
)

object ProjectManifest:
  private val SemVer =
    raw"""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
    raw"""(?:-((?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)""" +
    raw"""(?:\.(?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*))*))?""" +
    raw"""(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$$"""

  private val PackageName = "[a-z][a-z0-9]*(?:-[a-z0-9]+)*".r

  def load(path: Path): Either[ProjectError, ProjectManifest] =
    val bytes =
      try Files.readAllBytes(path)
      catch
        case error: IOException => return Left(ProjectError(s"Could not read project manifest: ${error.getMessage}", Some(error)))

    val result =
      try Toml.parse(path)
      catch
        case error: IOException => return Left(ProjectError(s"Could not parse project manifest: ${error.getMessage}", Some(error)))

    if result.hasErrors then
      Left(ProjectError(result.errors().toArray(Array.empty[TomlParseError]).sortBy(error =>
        (error.position().line(), error.position().column(), error.getMessage)
      ).map(renderParseError).mkString("\n")))
    else
      validateStructure(result) match
        case Left(error) => Left(error)
        case Right(packageTable) =>
          validateValue(packageTable, "name") match
            case Left(error) => Left(error)
            case Right(name) if !validName(name) => Left(errorAt(packageTable, "name", s"Invalid package name: $name"))
            case Right(name) =>
              validateValue(packageTable, "version") match
                case Left(error) => Left(error)
                case Right(version) if !validVersion(version) => Left(errorAt(packageTable, "version", s"Invalid package version: $version"))
                case Right(version) => Right(ProjectManifest(name, version, path, bytes))

  private def validateStructure(root: TomlTable): Either[ProjectError, TomlTable] =
    if !root.contains("package") then Left(ProjectError("Missing required [package] table"))
    else if !root.isTable("package") then Left(ProjectError("package must be a table"))
    else
      val unknownRoot = root.keySet().toArray(Array.empty[String]).filter(_ != "package").sorted.headOption
      unknownRoot match
        case Some(key) if root.isTable(key) => Left(errorAt(root, key, s"Unknown root table: $key"))
        case Some(key) => Left(errorAt(root, key, s"Unknown root key: $key"))
        case None =>
          val packageTable = root.getTable("package")
          packageTable.keySet().toArray(Array.empty[String]).filter(key => key != "name" && key != "version").sorted.headOption match
            case Some(key) => Left(errorAt(packageTable, key, s"Unknown package key: $key"))
            case None => Right(packageTable)

  private def validateValue(table: TomlTable, key: String): Either[ProjectError, String] =
    if !table.contains(key) then Left(ProjectError(s"Missing required package key: $key"))
    else if !table.isString(key) then Left(errorAt(table, key, s"package.$key must be a string"))
    else Right(table.getString(key))

  private def errorAt(table: TomlTable, key: String, message: String): ProjectError =
    ProjectError(renderPosition(table.inputPositionOf(key), message))

  private def renderParseError(error: TomlParseError): String =
    renderPosition(error.position(), error.getMessage)

  private def renderPosition(position: TomlPosition, message: String): String =
    s"line ${position.line()}, column ${position.column()}: $message"

  private[project] def validName(value: String): Boolean =
    PackageName.matches(value)

  private[project] def validVersion(value: String): Boolean =
    SemVer.r.matches(value)
