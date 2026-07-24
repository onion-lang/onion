package onion.tools.readiness.benchmark

import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Path}
import java.security.MessageDigest

final case class CompileWorkload(
  id: String,
  label: String,
  root: Path,
  relativeFiles: Vector[String],
  sourceCount: Int,
  lineCount: Int,
  byteCount: Long,
  workloadHash: String
):
  def paths: Vector[Path] = relativeFiles.map(root.resolve)

object CompileWorkload:
  def fromFiles(
    root: Path,
    id: String,
    label: String,
    relativeFiles: Vector[String]
  ): CompileWorkload =
    require(relativeFiles.nonEmpty, "compile workload requires at least one source")
    val normalizedRoot = root.toAbsolutePath.normalize()
    val digest = MessageDigest.getInstance("SHA-256")
    var lines = 0
    var bytes = 0L

    relativeFiles.foreach { relative =>
      val path = normalizedRoot.resolve(relative).normalize()
      require(path.startsWith(normalizedRoot), s"workload source escapes root: $relative")
      require(Files.isRegularFile(path), s"workload source does not exist: $relative")
      val content = Files.readAllBytes(path)
      digest.update(relative.getBytes(StandardCharsets.UTF_8))
      digest.update(0.toByte)
      digest.update(content)
      bytes += content.length
      lines += countLines(new String(content, StandardCharsets.UTF_8))
    }

    CompileWorkload(
      id = id,
      label = label,
      root = normalizedRoot,
      relativeFiles = relativeFiles,
      sourceCount = relativeFiles.size,
      lineCount = lines,
      byteCount = bytes,
      workloadHash = digest.digest().map(byte => f"${byte & 0xff}%02x").mkString
    )

  private def countLines(content: String): Int =
    if content.isEmpty then 0
    else content.count(_ == '\n') + (if content.endsWith("\n") then 0 else 1)
