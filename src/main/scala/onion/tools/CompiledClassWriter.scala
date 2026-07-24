package onion.tools

import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path

import onion.compiler.CompiledClass
import scala.collection.mutable
import scala.util.control.NonFatal

final case class CompiledClassWriteError(
  message: String,
  cause: Option[Throwable] = None
)

object CompiledClassWriter:
  private val BinaryNameSegment = "[A-Za-z_$][A-Za-z0-9_$]*"

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
        val created = mutable.LinkedHashSet.empty[Path]
        val written = Vector.newBuilder[Path]
        try
          planned.result().foreach { case (binary, target) =>
            Files.createDirectories(target.getParent)
            if !Files.exists(target, LinkOption.NOFOLLOW_LINKS) then created += target
            Files.write(target, binary.content)
            written += target
          }
          Right(written.result())
        catch
          case NonFatal(error) =>
            created.toVector.reverse.foreach { path =>
              try Files.deleteIfExists(path)
              catch case NonFatal(_) => ()
            }
            Left(CompiledClassWriteError("Failed to write compiled class files", Some(error)))
