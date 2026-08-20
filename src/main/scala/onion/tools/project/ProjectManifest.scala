package onion.tools.project

import java.nio.file.Files
import java.nio.file.Path
import java.io.IOException
import java.nio.charset.StandardCharsets

import org.tomlj.Toml
import org.tomlj.TomlParseError
import org.tomlj.TomlPosition
import org.tomlj.TomlTable

/** A single `group:artifact = "version"` entry from the manifest's `[dependencies]`. */
final case class Dependency(group: String, artifact: String, version: String):
  /** The Maven coordinate, which is also what a resolver and a lock file want. */
  def render: String = s"$group:$artifact:$version"

final case class ProjectManifest(
  name: String,
  version: String,
  path: Path,
  bytes: Array[Byte],
  dependencies: Seq[Dependency] = Seq.empty,
  /**
   * Extra Maven repositories, searched in the order written and *in addition to* Maven
   * Central rather than instead of it. Unlike [[dependencies]] this is deliberately not
   * sorted: repository order is resolution precedence, so reordering it is a real change.
   */
  repositories: Seq[String] = Seq.empty
)

object ProjectManifest:
  private val SemVer =
    raw"""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)""" +
    raw"""(?:-((?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*)""" +
    raw"""(?:\.(?:0|[1-9]\d*|[0-9A-Za-z-]*[A-Za-z-][0-9A-Za-z-]*))*))?""" +
    raw"""(?:\+([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?$$"""

  private val PackageName = "[A-Za-z][A-Za-z0-9_-]*".r
  private val PackageTableHeader = raw"""(?m)^[ \t]*\[[ \t]*package[ \t]*\][ \t]*(?:#.*)?$$""".r

  private val KnownRootKeys = Set("package", "dependencies", "repositories")

  /** `group:artifact` — exactly one colon, neither half empty, no whitespace. */
  private val Coordinate = raw"""([^\s:]+):([^\s:]+)""".r

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
                case Right(version) =>
                  for
                    dependencies <- parseDependencies(path, result)
                    repositories <- parseRepositories(path, result)
                  yield ProjectManifest(name, version, path, bytes, dependencies, repositories)

  private def validateStructure(path: Path, bytes: Array[Byte], root: TomlTable): Either[ProjectError, TomlTable] =
    if !root.contains("package") then Left(ProjectError("Missing required [package] table"))
    else if !hasPackageTableHeader(bytes) then Left(errorAt(path, root, "package", "package must be declared with [package]"))
    else if !root.isTable("package") then Left(errorAt(path, root, "package", "package must be a table"))
    else
      val unknownRoot =
        root.keySet().toArray(Array.empty[String]).filterNot(KnownRootKeys.contains).sorted.headOption
      unknownRoot match
        case Some(key) if root.isTable(key) => Left(errorAt(path, root, key, s"Unknown root table: $key"))
        case Some(key) => Left(errorAt(path, root, key, s"Unknown root key: $key"))
        case None if root.contains("dependencies") && !root.isTable("dependencies") =>
          Left(errorAt(path, root, "dependencies", "dependencies must be a table"))
        case None if root.contains("repositories") && !root.isArray("repositories") =>
          Left(errorAt(path, root, "repositories", RepositoryShape))
        case None =>
          val packageTable = root.getTable("package")
          packageTable.keySet().toArray(Array.empty[String]).filter(key => key != "name" && key != "version").sorted.headOption match
            case Some(key) => Left(errorAt(path, packageTable, key, s"Unknown package key: $key"))
            case None => Right(packageTable)

  /**
   * Reads `[dependencies]`, whose keys are Maven coordinates and whose values are versions:
   *
   * {{{
   * [dependencies]
   * "org.postgresql:postgresql" = "42.7.3"
   * }}}
   *
   * Every tomlj lookup here goes through the `List` overloads. The `String` ones re-parse
   * their argument as a *dotted key path*, which would split `org.postgresql:postgresql`
   * into a walk through tables named `org` and `postgresql:postgresql` and find nothing —
   * a coordinate with a dotted group is the normal case, not the exotic one.
   *
   * The result is sorted by coordinate so that reordering the file cannot change it. The
   * resolved set feeds the build fingerprint, and a build should not be invalidated by
   * someone alphabetising their manifest.
   */
  private def parseDependencies(path: Path, root: TomlTable): Either[ProjectError, Seq[Dependency]] =
    if !root.contains("dependencies") then Right(Seq.empty)
    else
      val table = root.getTable("dependencies")
      val entries = table.keySet().toArray(Array.empty[String]).sorted.map { key =>
        val at = java.util.List.of(key)
        key match
          case Coordinate(group, artifact) =>
            if !table.isString(at) then Left(versionError(path, table, at, key))
            else
              val version = table.getString(at)
              if version.isEmpty then Left(versionError(path, table, at, key))
              else Right(Dependency(group, artifact, version))
          case _ =>
            Left(errorAt(path, table, at,
              s"""Invalid dependency coordinate: $key (expected "group:artifact")"""))
      }
      entries.collectFirst { case Left(error) => error } match
        case Some(error) => Left(error)
        case None => Right(entries.collect { case Right(dependency) => dependency }.toSeq)

  /**
   * Reads the optional array of tables:
   *
   * {{{
   * [[repositories]]
   * url = "https://nexus.example.com/repository/maven-public"
   * }}}
   *
   * An array of tables rather than a plain `repositories = [...]` array because a bare
   * key-value pair is scoped to whatever table header precedes it: written after
   * `[package]` it would silently become `package.repositories` and be rejected as an
   * unknown package key. A table header resets that scope, so this form can be written
   * anywhere in the file. It also keeps declaration order, which is resolution
   * precedence, and leaves room for per-repository settings later.
   *
   * Only absolute `http`/`https`/`file` URLs are accepted. A relative or misspelled entry
   * would otherwise reach coursier and come back as a resolution failure blamed on the
   * dependency rather than on the repository that caused it.
   */
  private def parseRepositories(path: Path, root: TomlTable): Either[ProjectError, Seq[String]] =
    if !root.contains("repositories") then Right(Seq.empty)
    else
      val array = root.getArray("repositories")
      // Element-wise rather than `containsTables`, which tomlj deprecated to an outright
      // UnsupportedOperationException once arrays became heterogeneous.
      val entries = (0 until array.size()).map { index =>
        array.get(index) match
          case entry: TomlTable =>
            entry.keySet().toArray(Array.empty[String]).filter(_ != "url").sorted.headOption match
              case Some(key) => Left(errorAt(path, entry, key, s"Unknown repository key: $key"))
              case None if !entry.isString("url") =>
                Left(errorAt(path, root, "repositories", RepositoryShape))
              case None =>
                val url = entry.getString("url")
                if validRepository(url) then Right(url)
                else Left(errorAt(path, entry, "url", s"Invalid repository URL: $url"))
          case _ => Left(errorAt(path, root, "repositories", RepositoryShape))
      }
      entries.collectFirst { case Left(error) => error } match
        case Some(error) => Left(error)
        case None => Right(entries.collect { case Right(url) => url }.toSeq)

  private val RepositoryShape =
    "repositories must be declared as [[repositories]] tables with a url string"

  private[project] def validRepository(value: String): Boolean =
    try
      val uri = java.net.URI(value)
      uri.isAbsolute && Set("http", "https", "file").contains(uri.getScheme)
    catch case _: java.net.URISyntaxException => false

  private def versionError(path: Path, table: TomlTable, at: java.util.List[String], key: String): ProjectError =
    errorAt(path, table, at, s"""dependencies."$key" must be a version string""")

  private def validateValue(path: Path, table: TomlTable, key: String): Either[ProjectError, String] =
    if !table.contains(key) then Left(ProjectError(s"Missing required package key: $key"))
    else if !table.isString(key) then Left(errorAt(path, table, key, "package." + key + " must be a string"))
    else Right(table.getString(key))

  private def hasPackageTableHeader(bytes: Array[Byte]): Boolean =
    PackageTableHeader.findFirstIn(String(bytes, StandardCharsets.UTF_8)).nonEmpty

  private def errorAt(path: Path, table: TomlTable, key: String, message: String): ProjectError =
    errorAt(path, table, java.util.List.of(key), message)

  private def errorAt(path: Path, table: TomlTable, at: java.util.List[String], message: String): ProjectError =
    Option(table.inputPositionOf(at))
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
