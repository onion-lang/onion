package onion.tools.project

import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets.UTF_8
import java.security.MessageDigest

object BuildFingerprint:
  /** Bumped to 2 when the resolved dependency set joined the digest. */
  val SchemaVersion = 2

  /**
   * @param dependencyCoordinates the whole *resolved* set, not the manifest's direct
   *   entries. Hashing the manifest bytes alone is not enough: a transitive version can
   *   change without `onion.toml` changing a byte, and the cache would then serve classes
   *   compiled against the old classpath as if they were current.
   */
  def compute(
    manifestBytes: Array[Byte],
    sources: Vector[(String, Array[Byte])],
    compilerVersion: String,
    javaFeature: Int,
    dependencyCoordinates: Seq[String]
  ): String =
    val digest = MessageDigest.getInstance("SHA-256")
    update(digest, SchemaVersion.toString.getBytes(UTF_8))
    update(digest, compilerVersion.getBytes(UTF_8))
    update(digest, javaFeature.toString.getBytes(UTF_8))
    update(digest, manifestBytes)
    dependencyCoordinates.sorted.foreach(coordinate => update(digest, coordinate.getBytes(UTF_8)))
    sources.sortBy(_._1).foreach { case (relative, bytes) =>
      update(digest, relative.getBytes(UTF_8))
      update(digest, bytes)
    }
    digest.digest().map(byte => f"${byte & 0xff}%02x").mkString

  private def update(digest: MessageDigest, bytes: Array[Byte]): Unit =
    digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array())
    digest.update(bytes)
