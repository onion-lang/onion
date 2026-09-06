package onion.compiler.tools

import org.scalatest.funspec.AnyFunSpec

/**
 * Coverage guard for the `Args` module docs.
 *
 * `onion.Args.Parsed::option(String)` -- the single-argument overload that returns the
 * raw value of `--name`/`--name=value` or `null` when absent -- is a real, callable
 * method (see src/main/java/onion/Args.java) alongside the two-argument
 * `option(name, defaultValue)` form, but docs/reference/stdlib.md and its Japanese
 * translation only ever show `parsed.option("out", "a.out")`, never the one-argument
 * `parsed.option("out")` call, making the nullable-return overload undiscoverable
 * without reading the Java source.
 */
class ArgsDocCoverageSpec extends AnyFunSpec {

  private def read(p: String): String =
    java.nio.file.Files.readString(java.nio.file.Path.of(p))

  private def hasSingleArgOptionCall(doc: String): Boolean =
    """parsed\.option\("\w+"\)""".r.findFirstIn(doc).isDefined

  it("actual onion.Args.Parsed exposes the single-argument option(name) overload (sanity check)") {
    val hasSingleArgOption = classOf[onion.Args.Parsed].getDeclaredMethods.exists { m =>
      m.getName == "option" && m.getParameterCount == 1
    }
    assert(hasSingleArgOption,
      "reflection on onion.Args.Parsed found no one-argument option method -- the scan has rotted")
  }

  it("docs/reference/stdlib.md documents the single-argument Parsed::option(name) overload") {
    assert(hasSingleArgOptionCall(read("docs/reference/stdlib.md")),
      "docs/reference/stdlib.md never shows parsed.option(\"name\") with no default argument")
  }

  it("docs/ja/reference/stdlib.md documents the single-argument Parsed::option(name) overload") {
    assert(hasSingleArgOptionCall(read("docs/ja/reference/stdlib.md")),
      "docs/ja/reference/stdlib.md never shows parsed.option(\"name\") with no default argument")
  }
}
