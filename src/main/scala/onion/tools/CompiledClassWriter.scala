package onion.tools

import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardOpenOption

import onion.compiler.CompiledClass
import scala.collection.mutable
import scala.util.control.NonFatal

final case class CompiledClassWriteError(
  message: String,
  cause: Option[Throwable] = None
)

object CompiledClassWriter:
  private val BinaryNameSegment = "[A-Za-z_$][A-Za-z0-9_$]*"
  private val NoFollow = LinkOption.NOFOLLOW_LINKS

  def relativePath(className: String): Either[CompiledClassWriteError, Path] =
    val segments =
      if className == null then Vector.empty
      else className.split("\\.", -1).toVector
    if segments.isEmpty || segments.exists(segment => !segment.matches(BinaryNameSegment)) then
      Left(CompiledClassWriteError(s"Invalid compiled class name: ${String.valueOf(className)}"))
    else
      val directory = segments.dropRight(1).foldLeft(Path.of(""))(_.resolve(_))
      Right(directory.resolve(s"${segments.last}.class"))

  def writeAll(
    binaries: Seq[CompiledClass]
  ): Either[CompiledClassWriteError, Vector[Path]] =
    val planned = Vector.newBuilder[(CompiledClass, Path)]
    val iterator = binaries.iterator
    var planningError: Option[CompiledClassWriteError] = None

    while iterator.hasNext && planningError.isEmpty do
      val binary = iterator.next()
      relativePath(binary.className) match
        case Left(error) =>
          planningError = Some(error)
        case Right(relative) =>
          try
            val root = Path.of(binary.outputPath).toAbsolutePath.normalize
            val target = root.resolve(relative).normalize
            if !target.startsWith(root) then
              planningError = Some(CompiledClassWriteError(
                s"Compiled class path escapes output directory: ${binary.className}"
              ))
            else
              planned += binary -> target
          catch
            case NonFatal(error) =>
              planningError = Some(CompiledClassWriteError(
                s"Invalid output path for compiled class ${binary.className}: ${binary.outputPath}",
                Some(error)
              ))

    planningError match
      case Some(error) => Left(error)
      case None =>
        val createdFiles = mutable.LinkedHashSet.empty[Path]
        val createdDirectories = mutable.LinkedHashSet.empty[Path]
        val written = Vector.newBuilder[Path]
        try
          planned.result().foreach { case (binary, target) =>
            val root = Path.of(binary.outputPath).toAbsolutePath.normalize
            ensureOutputRoot(root, createdDirectories)
            ensureDirectoriesBelow(root, target.getParent, createdDirectories)
            writeFile(target, binary.content, createdFiles)
            written += target
          }
          Right(written.result())
        catch
          case NonFatal(error) =>
            val cleanupFailures = cleanup(createdFiles, createdDirectories)
            cleanupFailures.foreach(error.addSuppressed)
            val message =
              if cleanupFailures.isEmpty then "Failed to write compiled class files"
              else s"Failed to write compiled class files; cleanup incomplete (${cleanupFailures.size} failure(s))"
            Left(CompiledClassWriteError(message, Some(error)))

  private def ensureOutputRoot(
    root: Path,
    createdDirectories: mutable.LinkedHashSet[Path]
  ): Unit =
    if Files.exists(root, NoFollow) then
      requireRealDirectory(root, "output root")
    else
      val missing = mutable.ArrayBuffer.empty[Path]
      var current: Path | Null = root
      while current != null && !Files.exists(current, NoFollow) do
        missing += current
        current = current.getParent
      if current == null || Files.isSymbolicLink(current) || !Files.isDirectory(current, NoFollow) then
        throw IOException(s"Output root has no existing directory ancestor: $root")
      missing.reverseIterator.foreach(path => createDirectory(path, createdDirectories))

  private def ensureDirectoriesBelow(
    root: Path,
    directory: Path,
    createdDirectories: mutable.LinkedHashSet[Path]
  ): Unit =
    if !directory.startsWith(root) then
      throw IOException(s"Compiled class directory escapes output root: $directory")
    requireRealDirectory(root, "output root")
    val segments = root.relativize(directory).iterator
    var current = root
    while segments.hasNext do
      current = current.resolve(segments.next())
      if Files.exists(current, NoFollow) then
        requireRealDirectory(current, "compiled class directory")
      else
        createDirectory(current, createdDirectories)

  private def createDirectory(
    directory: Path,
    createdDirectories: mutable.LinkedHashSet[Path]
  ): Unit =
    try
      Files.createDirectory(directory)
      createdDirectories += directory
    catch
      case _: FileAlreadyExistsException =>
        requireRealDirectory(directory, "compiled class directory")

  private def requireRealDirectory(path: Path, description: String): Unit =
    if Files.isSymbolicLink(path) || !Files.isDirectory(path, NoFollow) then
      throw IOException(s"$description is not a real directory: $path")

  private def writeFile(
    target: Path,
    content: Array[Byte],
    createdFiles: mutable.LinkedHashSet[Path]
  ): Unit =
    try
      Files.createFile(target)
      createdFiles += target
    catch
      case _: FileAlreadyExistsException =>
        if Files.isSymbolicLink(target) then
          throw IOException(s"Compiled class target is a symbolic link: $target")
    Files.write(
      target,
      content,
      StandardOpenOption.WRITE,
      StandardOpenOption.TRUNCATE_EXISTING,
      NoFollow
    )

  private def cleanup(
    createdFiles: mutable.LinkedHashSet[Path],
    createdDirectories: mutable.LinkedHashSet[Path]
  ): Vector[Throwable] =
    val failures = Vector.newBuilder[Throwable]
    createdFiles.toVector.reverseIterator.foreach(path => deleteTracked(path, failures))
    createdDirectories.toVector.reverseIterator.foreach(path => deleteTracked(path, failures))
    failures.result()

  private def deleteTracked(path: Path, failures: mutable.Builder[Throwable, Vector[Throwable]]): Unit =
    try Files.deleteIfExists(path)
    catch case NonFatal(error) => failures += error
