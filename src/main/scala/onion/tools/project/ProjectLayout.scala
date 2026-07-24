package onion.tools.project

import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path

import scala.jdk.CollectionConverters.IteratorHasAsScala
import scala.util.Using

final case class ProjectSource(path: Path, relative: String)

final case class ProjectLayout(
  productionSources: Vector[ProjectSource],
  testSources: Vector[ProjectSource]
)

object ProjectLayout:
  def discover(paths: ProjectPaths): Either[ProjectError, ProjectLayout] =
    try
      for
        productionSources <- sourceFiles(paths.root.resolve("src"), paths.root, path => !isTest(path))
        testSources <- sourceFiles(paths.root.resolve("tests"), paths.root, isTest)
      yield ProjectLayout(productionSources, testSources)
    catch
      case error: IOException => Left(ProjectError(s"Could not discover project sources: ${error.getMessage}", Some(error)))

  private def sourceFiles(directory: Path, root: Path, include: Path => Boolean): Either[ProjectError, Vector[ProjectSource]] =
    if !Files.isDirectory(directory, NOFOLLOW_LINKS) then Right(Vector.empty)
    else
      try
        val sources = Using.resource(Files.walk(directory)) { paths =>
          paths.iterator.asScala
            .filter(path => Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path))
            .filter(path => path.getFileName.toString.endsWith(".on"))
            .filter(include)
            .map(path => ProjectSource(path, root.relativize(path).toString.replace('\\', '/')))
            .toVector
            .sortBy(_.relative)
        }
        Right(sources)
      catch
        case error: IOException => Left(ProjectError(s"Could not discover sources in $directory: ${error.getMessage}", Some(error)))

  private def isTest(path: Path): Boolean =
    path.getFileName.toString.endsWith("_test.on")
