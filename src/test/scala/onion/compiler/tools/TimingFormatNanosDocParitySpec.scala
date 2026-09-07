package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * `Timing::formatNanos`'s microsecond band formats with a literal ASCII `"us"`
 * (`String.format("%.2fus", ...)` in `Timing.formatNanos`), but its own Javadoc and
 * both `docs/reference/stdlib.md` copies claimed the Greek `"μs"` instead. Pinned here
 * against the actual runtime output -- not just the doc text -- so the docs can't
 * drift back to a unit sign the implementation never produces.
 */
class TimingFormatNanosDocParitySpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  it("Timing::formatNanos never emits the Greek mu sign (sanity check)") {
    assert(onion.Timing.formatNanos(123L) == "123ns")
    assert(onion.Timing.formatNanos(45670L) == "45.67us")
    assert(onion.Timing.formatNanos(12340000L) == "12.34ms")
    assert(onion.Timing.formatNanos(1230000000L) == "1.23s")
  }

  it("Timing.java's Javadoc example matches the actual unit signs") {
    assert(read("src/main/java/onion/Timing.java").contains(
      """Examples: "500ns", "1.23us", "4.56ms", "1.23s""""))
  }

  it("docs/reference/stdlib.md's formatNanos example matches the actual unit signs") {
    assert(read("docs/reference/stdlib.md").contains(
      """// Output formats: "123ns", "45.67us", "12.34ms", "1.23s""""))
  }

  it("docs/ja/reference/stdlib.md's formatNanos example matches the actual unit signs") {
    assert(read("docs/ja/reference/stdlib.md").contains(
      """// 出力形式: "123ns", "45.67us", "12.34ms", "1.23s""""))
  }
}
