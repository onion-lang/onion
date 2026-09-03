package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Timing` module docs.
 *
 * `onion.Timing::measure` is overloaded: `measure(fn)` and the labeled
 * `measure(label, fn)` (mirroring `measureVoid`, which documents both its
 * unlabeled and labeled forms). docs/reference/stdlib.md and its Japanese
 * translation only ever showed the unlabeled `Timing::measure(...)` call,
 * never the labeled `Timing::measure("task", ...)` form -- even though the
 * analogous `Timing::measureVoid("task", ...)` example is right next to it.
 */
class TimingDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasLabeledMeasureCall(doc: String): Boolean =
    """Timing::measure\(\s*"""".r.findFirstIn(doc).isDefined

  it("actual onion.Timing::measure has both an unlabeled and a labeled overload (sanity check)") {
    val arities = classOf[onion.Timing].getDeclaredMethods
      .filter(_.getName == "measure")
      .map(_.getParameterCount)
      .toSet
    assert(arities == Set(1, 2), s"expected measure/1 and measure/2 overloads, found arities: ${arities.toSeq.sorted}")
  }

  it("docs/reference/stdlib.md demonstrates the labeled Timing::measure(label, fn) overload") {
    assert(hasLabeledMeasureCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows a labeled Timing::measure(\"...\", ...) call")
  }

  it("docs/ja/reference/stdlib.md demonstrates the labeled Timing::measure(label, fn) overload") {
    assert(hasLabeledMeasureCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows a labeled Timing::measure(\"...\", ...) call")
  }
}
