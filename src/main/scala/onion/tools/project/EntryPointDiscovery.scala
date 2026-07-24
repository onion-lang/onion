package onion.tools.project

import java.nio.file.Path
import java.nio.file.Paths as NioPaths

import onion.compiler.AST
import onion.compiler.toolbox.Paths as CompilerPaths
import scala.jdk.CollectionConverters.*
import scala.util.control.NonFatal

object EntryPointDiscovery:
  private final case class SourceUnit(
    unit: AST.CompilationUnit,
    source: String,
    className: String
  )

  def discover(
    root: Path,
    units: Seq[AST.CompilationUnit]
  ): Either[ProjectError, Vector[EntryPoint]] =
    for
      canonicalRoot <- canonicalizeRoot(root)
      sourceUnits <- normalizeUnits(canonicalRoot, units)
      _ <- rejectClassNameCollisions(sourceUnits)
    yield
      sourceUnits.flatMap(entryPoint).sortBy(entryPointKey)

  def testEntry(
    root: Path,
    unit: AST.CompilationUnit
  ): Either[ProjectError, EntryPoint] =
    for
      canonicalRoot <- canonicalizeRoot(root)
      sourceUnit <- normalizeUnit(canonicalRoot, unit)
    yield
      EntryPoint(
        sourceUnit.className,
        sourceUnit.source,
        unit.location.line,
        unit.location.column
      )

  private def canonicalizeRoot(root: Path): Either[ProjectError, Path] =
    try Right(root.toRealPath())
    catch
      case NonFatal(error) =>
        Left(ProjectError(s"Could not resolve project root $root: ${error.getMessage}", Some(error)))

  private def normalizeUnits(
    canonicalRoot: Path,
    units: Seq[AST.CompilationUnit]
  ): Either[ProjectError, Vector[SourceUnit]] =
    units.foldLeft(Right(Vector.empty): Either[ProjectError, Vector[SourceUnit]]) {
      case (result, unit) =>
        for
          normalized <- result
          sourceUnit <- normalizeUnit(canonicalRoot, unit)
        yield normalized :+ sourceUnit
    }

  private def normalizeUnit(
    canonicalRoot: Path,
    unit: AST.CompilationUnit
  ): Either[ProjectError, SourceUnit] =
    Option(unit.sourceFile).filter(_.nonEmpty) match
      case None =>
        Left(ProjectError("Parsed source has no source file"))
      case Some(sourceFile) =>
        try
          val path = NioPaths.get(sourceFile)
          val resolved =
            if path.isAbsolute then path
            else canonicalRoot.resolve(path)
          val canonicalSource = resolved.toRealPath()
          if canonicalSource == canonicalRoot || !canonicalSource.startsWith(canonicalRoot) then
            Left(ProjectError(s"Source file is outside project root: $canonicalSource"))
          else
            val relative =
              canonicalRoot.relativize(canonicalSource).iterator().asScala.mkString("/")
            val simple = CompilerPaths.cutExtension(sourceFile) + "Main"
            val className =
              Option(unit.module).map(module => s"${module.name}.$simple").getOrElse(simple)
            Right(SourceUnit(unit, relative, className))
        catch
          case NonFatal(error) =>
            Left(ProjectError(s"Could not resolve source file $sourceFile: ${error.getMessage}", Some(error)))

  private def rejectClassNameCollisions(
    units: Vector[SourceUnit]
  ): Either[ProjectError, Unit] =
    val collision =
      units
        .groupBy(_.className)
        .toVector
        .sortBy(_._1)
        .collectFirst {
          case (className, matching) if matching.map(_.source).distinct.size > 1 =>
            className -> matching.map(_.source).distinct.sorted
        }

    collision match
      case Some((className, sources)) =>
        Left(
          ProjectError(
            s"Generated top-level class collision for $className: ${sources.mkString(", ")}"
          )
        )
      case None =>
        Right(())

  private def entryPoint(sourceUnit: SourceUnit): Option[EntryPoint] =
    val explicit =
      sourceUnit.unit.toplevels.collectFirst {
        case function: AST.FunctionDeclaration if function.name == "main" =>
          function.location
      }
    val conventional =
      if sourceUnit.source == "src/main.on" then
        sourceUnit.unit.toplevels.collectFirst {
          case element: AST.BlockElement => element.location
        }
      else None

    explicit.orElse(conventional).map { location =>
      EntryPoint(
        sourceUnit.className,
        sourceUnit.source,
        location.line,
        location.column
      )
    }

  private def entryPointKey(entryPoint: EntryPoint): (String, Int, Int, String) =
    (entryPoint.source, entryPoint.line, entryPoint.column, entryPoint.className)
