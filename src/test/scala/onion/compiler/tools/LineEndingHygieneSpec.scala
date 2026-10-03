package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec
import java.nio.file.{Files, Path}
import scala.jdk.CollectionConverters._

/**
 * Regression guard for issue #1930: `.gitattributes` forces `*.scala` and `*.on` to
 * check out with LF line endings (so a Windows clone with `core.autocrlf=true` does not
 * silently turn every `"""..."""` fixture or sample into a CRLF string), but nothing
 * enforced that committed content actually has no `\r` in it. A file added without the
 * attribute applying (for example checked in from a Windows editor before `git add`
 * normalizes it, or copied in by some other tool) would carry CRLF forever and this
 * project's Linux CI would never notice, since Linux never re-encodes on checkout.
 */
class LineEndingHygieneSpec extends AnyFunSpec {

  private val roots = Seq(Path.of("src"), Path.of("run"), Path.of("benchmarks/fixtures"))
  private val extensions = Set(".scala", ".on")

  private lazy val trackedFiles: Seq[Path] =
    roots.filter(Files.exists(_)).flatMap { root =>
      val stream = Files.walk(root)
      try
        stream.iterator().asScala
          .filter(p => Files.isRegularFile(p) && extensions.exists(p.toString.endsWith))
          .toSeq
      finally stream.close()
    }

  it("scans a non-empty set of files") {
    assert(trackedFiles.size > 100, s"expected many .scala/.on files under ${roots.mkString(", ")}, found ${trackedFiles.size} — the scan has rotted")
  }

  it("every tracked .scala/.on file has no CRLF or bare CR") {
    val offenders = trackedFiles.filter { p =>
      val bytes = Files.readAllBytes(p)
      bytes.contains('\r'.toByte)
    }.map(_.toString).sorted
    assert(
      offenders.isEmpty,
      s"found \\r in files that .gitattributes says must be LF-only (re-add after fixing to renormalize):\n${offenders.mkString("\n")}"
    )
  }
}
