package onion.tools.project

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

object BuildFingerprint:
  val SchemaVersion = 1

  def compute(
    manifestBytes: Array[Byte],
    sources: Vector[(String, Array[Byte])],
    compilerVersion: String,
    javaFeature: Int
  ): String =
    val digest = MessageDigest.getInstance("SHA-256")
    update(digest, SchemaVersion.toString.getBytes(UTF_8))
    update(digest, compilerVersion.getBytes(UTF_8))
    update(digest, javaFeature.toString.getBytes(UTF_8))
    update(digest, manifestBytes)
    sources.sortBy(_._1).foreach { case (relative, bytes) =>
      update(digest, relative.getBytes(UTF_8))
      update(digest, bytes)
    }
    digest.digest().map(byte => f"${byte & 0xff}%02x").mkString

  private def update(digest: MessageDigest, bytes: Array[Byte]): Unit =
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array())
    digest.update(bytes)
