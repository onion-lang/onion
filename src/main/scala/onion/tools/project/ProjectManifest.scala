package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import java.nio.charset.StandardCharsets

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

  private val PackageName = "[A-Za-z][A-Za-z0-9_-]*".r
  private val PackageTableHeader = raw"""(?m)^[ \t]*\[[ \t]*package[ \t]*\][ \t]*(?:#.*)?$$""".r

  def load(path: Path): Either[ProjectError, ProjectManifest] =
    val bytes =
      try Files.readAllBytes(path)
      catch
        case error: IOException => return Left(ProjectError(s"Could not read project manifest: ${error.getMessage}", Some(error)))

    val result =
      try Toml.parse(path)
      catch
        case error: IOException => return Left(ProjectError(s"Could not parse project manifest: ${error.getMessage}", Some(error)))
        case error: StackOverflowError =>
          return Left(ProjectError(s"Could not parse project manifest: too deeply nested", Some(error)))

    if result.hasErrors then
      Left(ProjectError(result.errors().toArray(Array.empty[TomlParseError]).sortBy(error =>
        (error.position().line(), error.position().column(), error.getMessage)
      ).map(renderParseError(path, _)).mkString("\n")))
    else
      validateStructure(path, bytes, result) match
        case Left(error) => Left(error)
        case Right(packageTable) =>
          validateValue(path, packageTable, "name") match
            case Left(error) => Left(error)
            case Right(name) if !validName(name) => Left(errorAt(path, packageTable, "name", s"Invalid package name: $name"))
            case Right(name) =>
              validateValue(path, packageTable, "version") match
                case Left(error) => Left(error)
                case Right(version) if !validVersion(version) => Left(errorAt(path, packageTable, "version", s"Invalid package version: $version"))
                case Right(version) => Right(ProjectManifest(name, version, path, bytes))

  private def validateStructure(path: Path, bytes: Array[Byte], root: TomlTable): Either[ProjectError, TomlTable] =
    if !root.contains("package") then Left(ProjectError("Missing required [package] table"))
    else if !hasPackageTableHeader(bytes) then Left(errorAt(path, root, "package", "package must be declared with [package]"))
    else if !root.isTable("package") then Left(errorAt(path, root, "package", "package must be a table"))
    else
      val unknownRoot = root.keySet().toArray(Array.empty[String]).filter(_ != "package").sorted.headOption
      unknownRoot match
        case Some(key) if root.isTable(key) => Left(errorAt(path, root, key, s"Unknown root table: $key"))
        case Some(key) => Left(errorAt(path, root, key, s"Unknown root key: $key"))
        case None =>
          val packageTable = root.getTable("package")
          packageTable.keySet().toArray(Array.empty[String]).filter(key => key != "name" && key != "version").sorted.headOption match
            case Some(key) => Left(errorAt(path, packageTable, key, s"Unknown package key: $key"))
            case None => Right(packageTable)

  private def validateValue(path: Path, table: TomlTable, key: String): Either[ProjectError, String] =
    if !table.contains(key) then Left(ProjectError(s"Missing required package key: $key"))
    else if !table.isString(key) then Left(errorAt(path, table, key, "package." + key + " must be a string"))
    else Right(table.getString(key))

  private def hasPackageTableHeader(bytes: Array[Byte]): Boolean =
    PackageTableHeader.findFirstIn(String(bytes, StandardCharsets.UTF_8)).nonEmpty

  private def errorAt(path: Path, table: TomlTable, key: String, message: String): ProjectError =
    Option(table.inputPositionOf(key))
      .map(renderPosition(path, _, message))
      .map(ProjectError(_))
      .getOrElse(ProjectError(message))

  private def renderParseError(path: Path, error: TomlParseError): String =
    renderPosition(path, error.position(), error.getMessage)

  private def renderPosition(path: Path, position: TomlPosition, message: String): String =
    path.getFileName.toString + ":" + position.line() + ":" + position.column() + ": " + message

  private[project] def validName(value: String): Boolean =
    PackageName.matches(value)

  private[project] def validVersion(value: String): Boolean =
    SemVer.r.matches(value)
