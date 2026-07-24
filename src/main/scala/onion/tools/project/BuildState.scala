package onion.tools.project

import java.io.IOException
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.util.ArrayList
import java.util.LinkedHashMap
import java.util.List as JavaList
import java.util.Map as JavaMap

import onion.Json
import onion.tools.CompiledClassWriter
import scala.jdk.CollectionConverters.*

final case class EntryPoint(
  className: String,
  source: String,
  line: Int,
  column: Int
)

final case class BuildState(
  schemaVersion: Int,
  fingerprint: String,
  classes: Vector[String],
  entryPoints: Vector[EntryPoint]
)

object BuildState:
  private val RootFields = Set("schemaVersion", "fingerprint", "classes", "entryPoints")
  private val EntryPointFields = Set("className", "source", "line", "column")
  private val FingerprintPattern = "[0-9a-f]{64}"
  private val WindowsDrivePattern = "(?i)[a-z]:.*"

  def encode(state: BuildState): String =
    val root = LinkedHashMap[String, Object]()
    root.put("schemaVersion", Int.box(state.schemaVersion))
    root.put("fingerprint", state.fingerprint)
    root.put("classes", strings(state.classes.sorted))

    val entryPoints = ArrayList[Object]()
    state.entryPoints.sortBy(entryPointKey).foreach { entryPoint =>
      val encoded = LinkedHashMap[String, Object]()
      encoded.put("className", entryPoint.className)
      encoded.put("source", entryPoint.source)
      encoded.put("line", Int.box(entryPoint.line))
      encoded.put("column", Int.box(entryPoint.column))
      entryPoints.add(encoded)
    }
    root.put("entryPoints", entryPoints)
    Json.stringifyPretty(root)

  def decode(text: String): Either[ProjectError, BuildState] =
    try
      val parsed = Json.parse(text)
      val root = Json.asObject(parsed)
      if root == null then invalid("root must be an object")
      else decodeRoot(root)
    catch
      case error: Json.JsonParseException =>
        Left(ProjectError(s"Could not parse build state: ${error.getMessage}", Some(error)))

  def load(path: Path): Either[ProjectError, BuildState] =
    try decode(Files.readString(path, UTF_8))
    catch
      case error: IOException =>
        Left(ProjectError(s"Could not read build state $path: ${error.getMessage}", Some(error)))

  def write(path: Path, state: BuildState): Either[ProjectError, Unit] =
    try
      Files.writeString(path, encode(state), UTF_8)
      Right(())
    catch
      case error: IOException =>
        Left(ProjectError(s"Could not write build state $path: ${error.getMessage}", Some(error)))

  def validatesOutputs(state: BuildState, classesRoot: Path): Boolean =
    if validateClasses(state.classes).isLeft then false
    else
      val root = classesRoot.toAbsolutePath.normalize
      Files.isDirectory(root, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(root) &&
        state.classes.forall(classOutputExists(root, _))

  private def decodeRoot(root: JavaMap[String, Object]): Either[ProjectError, BuildState] =
    for
      _ <- exactFields(root, RootFields, "root")
      schemaVersion <- integerField(root, "schemaVersion")
      _ <-
        if schemaVersion == BuildFingerprint.SchemaVersion then Right(())
        else invalid(s"unsupported schemaVersion: $schemaVersion")
      fingerprint <- stringField(root, "fingerprint")
      _ <-
        if fingerprint.matches(FingerprintPattern) then Right(())
        else invalid("fingerprint must be 64 lowercase hexadecimal characters")
      classes <- stringArrayField(root, "classes")
      _ <- validateClasses(classes)
      entryPoints <- entryPointArrayField(root, "entryPoints")
      _ <- validateEntryPoints(entryPoints, classes.toSet)
    yield BuildState(schemaVersion, fingerprint, classes, entryPoints)

  private def exactFields(
    value: JavaMap[String, Object],
    expected: Set[String],
    context: String
  ): Either[ProjectError, Unit] =
    val actual = value.keySet().asScala.toSet
    val missing = expected -- actual
    val unknown = actual -- expected
    if missing.nonEmpty then invalid(s"$context is missing field(s): ${missing.toVector.sorted.mkString(", ")}")
    else if unknown.nonEmpty then invalid(s"$context has unknown field(s): ${unknown.toVector.sorted.mkString(", ")}")
    else Right(())

  private def stringField(
    value: JavaMap[String, Object],
    field: String
  ): Either[ProjectError, String] =
    value.get(field) match
      case result: String => Right(result)
      case _ => invalid(s"$field must be a string")

  private def integerField(
    value: JavaMap[String, Object],
    field: String
  ): Either[ProjectError, Int] =
    value.get(field) match
      case result: java.lang.Long
          if result.longValue >= Int.MinValue && result.longValue <= Int.MaxValue =>
        Right(result.intValue)
      case result: java.lang.Double
          if java.lang.Double.isFinite(result.doubleValue) &&
            result.doubleValue == Math.rint(result.doubleValue) &&
            result.doubleValue >= Int.MinValue &&
            result.doubleValue <= Int.MaxValue =>
        Right(result.intValue)
      case _ => invalid(s"$field must be an in-range integer")

  private def arrayField(
    value: JavaMap[String, Object],
    field: String
  ): Either[ProjectError, JavaList[Object]] =
    val array = Json.asArray(value.get(field))
    if array == null then invalid(s"$field must be an array")
    else Right(array)

  private def stringArrayField(
    value: JavaMap[String, Object],
    field: String
  ): Either[ProjectError, Vector[String]] =
    arrayField(value, field).flatMap { array =>
      val builder = Vector.newBuilder[String]
      val iterator = array.iterator()
      var error: Option[ProjectError] = None
      while iterator.hasNext && error.isEmpty do
        iterator.next() match
          case item: String => builder += item
          case _ => error = Some(ProjectError(s"$field entries must be strings"))
      error.toLeft(builder.result())
    }

  private def entryPointArrayField(
    value: JavaMap[String, Object],
    field: String
  ): Either[ProjectError, Vector[EntryPoint]] =
    arrayField(value, field).flatMap { array =>
      val builder = Vector.newBuilder[EntryPoint]
      val iterator = array.iterator()
      var error: Option[ProjectError] = None
      while iterator.hasNext && error.isEmpty do
        val item = Json.asObject(iterator.next())
        if item == null then error = Some(ProjectError("entryPoints entries must be objects"))
        else
          decodeEntryPoint(item) match
            case Left(problem) => error = Some(problem)
            case Right(entryPoint) => builder += entryPoint
      error.toLeft(builder.result())
    }

  private def decodeEntryPoint(
    value: JavaMap[String, Object]
  ): Either[ProjectError, EntryPoint] =
    for
      _ <- exactFields(value, EntryPointFields, "entryPoint")
      className <- stringField(value, "className")
      source <- stringField(value, "source")
      line <- integerField(value, "line")
      column <- integerField(value, "column")
    yield EntryPoint(className, source, line, column)

  private def validateClasses(classes: Vector[String]): Either[ProjectError, Unit] =
    if classes.isEmpty then invalid("classes must be nonempty")
    else if classes.distinct.size != classes.size then invalid("classes must not contain duplicates")
    else if classes != classes.sorted then invalid("classes must be sorted")
    else
      classes.find(CompiledClassWriter.relativePath(_).isLeft) match
        case Some(className) => invalid(s"unsafe class name: $className")
        case None => Right(())

  private def validateEntryPoints(
    entryPoints: Vector[EntryPoint],
    classes: Set[String]
  ): Either[ProjectError, Unit] =
    val invalidPosition = entryPoints.find(entryPoint => entryPoint.line < 1 || entryPoint.column < 1)
    val invalidSource = entryPoints.find(entryPoint => !validSource(entryPoint.source))
    val invalidClass = entryPoints.find(entryPoint => !classes.contains(entryPoint.className))
    if invalidPosition.nonEmpty then invalid("entrypoint line and column must be at least 1")
    else if invalidSource.nonEmpty then invalid(s"invalid entrypoint source: ${invalidSource.get.source}")
    else if invalidClass.nonEmpty then invalid(s"entrypoint class is absent from classes: ${invalidClass.get.className}")
    else if !strictlySortedEntryPoints(entryPoints) then invalid("entryPoints must be sorted without duplicates")
    else Right(())

  private def validSource(source: String): Boolean =
    if source.isEmpty ||
      source.contains('\\') ||
      source.startsWith("/") ||
      source.matches(WindowsDrivePattern)
    then false
    else
      val segments = source.split("/", -1)
      if segments.exists(segment => segment.isEmpty || segment == "." || segment == "..") then false
      else
        try
          val path = Path.of(source)
          !path.isAbsolute && path.normalize.toString.replace('\\', '/') == source
        catch
          case _: InvalidPathException => false

  private def strictlySortedEntryPoints(entryPoints: Vector[EntryPoint]): Boolean =
    entryPoints.zip(entryPoints.drop(1)).forall { case (left, right) =>
      Ordering[(String, Int, Int, String)].lt(entryPointKey(left), entryPointKey(right))
    }

  private def entryPointKey(entryPoint: EntryPoint): (String, Int, Int, String) =
    (entryPoint.source, entryPoint.line, entryPoint.column, entryPoint.className)

  private def strings(values: Vector[String]): JavaList[Object] =
    val result = ArrayList[Object]()
    values.foreach(result.add)
    result

  private def classOutputExists(root: Path, className: String): Boolean =
    CompiledClassWriter.relativePath(className).toOption.exists { relative =>
      val target = root.resolve(relative).normalize
      target.startsWith(root) &&
        realDirectories(root, target.getParent) &&
        Files.isRegularFile(target, NOFOLLOW_LINKS) &&
        !Files.isSymbolicLink(target)
    }

  private def realDirectories(root: Path, directory: Path): Boolean =
    val iterator = root.relativize(directory).iterator()
    var current = root
    var valid = true
    while iterator.hasNext && valid do
      current = current.resolve(iterator.next())
      valid = Files.isDirectory(current, NOFOLLOW_LINKS) && !Files.isSymbolicLink(current)
    valid

  private def invalid[A](message: String): Either[ProjectError, A] =
    Left(ProjectError(s"Invalid build state: $message"))
