package onion.tools.project

import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

private[project] object FileTree:
  def delete(path: Path, trustedBoundary: Path): Unit =
    val boundary = canonicalBoundary(trustedBoundary)
    val target = path.toAbsolutePath.normalize
    if target == boundary || !target.startsWith(boundary) then
      throw IOException(s"Refusing to delete path outside trusted boundary $boundary: $target")
    if !Files.exists(target, NOFOLLOW_LINKS) then return

    requireRealAncestors(boundary, target.getParent)
    Files.walkFileTree(
      target,
      new SimpleFileVisitor[Path]:
        override def visitFile(
          file: Path,
          attributes: BasicFileAttributes
        ): FileVisitResult =
          Files.delete(file)
          FileVisitResult.CONTINUE

        override def visitFileFailed(
          file: Path,
          error: IOException
        ): FileVisitResult =
          throw error

        override def postVisitDirectory(
          directory: Path,
          error: IOException | Null
        ): FileVisitResult =
          if error != null then throw error
          Files.delete(directory)
          FileVisitResult.CONTINUE
    )

  private def canonicalBoundary(path: Path): Path =
    val normalized = path.toAbsolutePath.normalize
    if Files.isSymbolicLink(normalized) ||
      !Files.isDirectory(normalized, NOFOLLOW_LINKS)
    then
      throw IOException(s"Trusted boundary is not a real directory: $normalized")
    val canonical = normalized.toRealPath()
    if canonical != normalized then
      throw IOException(s"Trusted boundary is not canonical: $normalized")
    canonical

  private def requireRealAncestors(boundary: Path, parent: Path): Unit =
    val iterator = boundary.relativize(parent).iterator()
    var current = boundary
    while iterator.hasNext do
      current = current.resolve(iterator.next())
      if Files.isSymbolicLink(current) ||
        !Files.isDirectory(current, NOFOLLOW_LINKS)
      then
        throw IOException(s"Deletion path has a non-directory or symbolic-link ancestor: $current")
