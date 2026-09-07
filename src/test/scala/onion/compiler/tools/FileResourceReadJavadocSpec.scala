package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Guards against `FileResource#read(Shape)`'s Javadoc being orphaned above
 * `readLossless(Shape)` instead of directly documenting `read`. The two
 * methods sit back-to-back with two `/** ... */` blocks between them; only
 * the second block attaches to a declaration under normal Javadoc adjacency
 * (the one right above `readLossless`), leaving `read` itself undocumented
 * and the first block ("Named `read` rather than `as`...", which plainly
 * describes `read`, not `readLossless`) dead text. Compare the sibling
 * `HttpResource#read(Shape)`, which carries the same doc text correctly
 * attached to its own declaration.
 */
class FileResourceReadJavadocSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  describe("FileResource#read(Shape)'s Javadoc") {
    it("sits directly above read(Shape<T> shape), not above readLossless(Shape<T> shape)") {
      val src = read("src/main/java/onion/FileResource.java")

      val docMarker = "Named `read` rather than `as`"
      val docIdx = src.indexOf(docMarker)
      assert(docIdx >= 0, "expected the 'Named `read`...' doc comment to exist in FileResource.java")

      val readLosslessIdx = src.indexOf("public <T> Outcome<Lossless<T>> readLossless(Shape<T> shape)")
      val readIdx = src.indexOf("public <T> Outcome<T> read(Shape<T> shape)")
      assert(readLosslessIdx >= 0, "expected readLossless(Shape<T> shape) to exist in FileResource.java")
      assert(readIdx >= 0, "expected read(Shape<T> shape) to exist in FileResource.java")

      assert(
        readLosslessIdx < docIdx,
        "the 'Named `read`...' doc comment must come after readLossless's own signature, " +
          "not sit above it"
      )
      assert(
        docIdx < readIdx,
        "the 'Named `read`...' doc comment must precede read(Shape<T> shape)"
      )

      val commentEndIdx = src.indexOf("*/", docIdx) + 2
      val between = src.substring(commentEndIdx, readIdx)
      assert(
        between.trim.isEmpty,
        "nothing but whitespace should separate the doc comment from read(Shape<T> shape) -- " +
          s"found: ${between.trim}"
      )
    }
  }
}
