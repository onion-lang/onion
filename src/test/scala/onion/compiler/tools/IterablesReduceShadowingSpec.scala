package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `onion.Colls` also declares a three-arg `reduce(List<T> list, U initial,
 * Function2<U, T, U> f)` with the same erased signature as `onion.Iterables`'s
 * `reduce(List<T> list, Object initial, Function2<Object, T, Object> reducer)`,
 * and `Colls` is registered ahead of `Iterables` in
 * `ExtensionMethodFallbackSupport.BuiltinExtensionContainers`, so `xs.reduce(initial, f)`
 * always reaches *`onion.Colls`*'s implementation -- `onion.Iterables`'s three-arg
 * `reduce` is never reachable by extension-call syntax at all, even though
 * docs/reference/stdlib.md (and its Japanese translation) list
 * `xs.reduce(0, (acc, x) -> acc + x)` as a plain `Iterables` chaining example
 * alongside `map`/`filter`/`take`/`drop`/`reverse` (already documented as shadowed)
 * without calling out the same shadowing for `reduce`.
 *
 * Unlike those runtime-only edge cases, this one is visible at *compile time*:
 * `Colls::reduce`'s declared signature types `initial`/the return value as the
 * actual generic `U`, so the compiler infers a concrete type (e.g. `Int`) for the
 * accumulator; `Iterables::reduce`'s declared signature erases both to plain
 * `Object`. So `xs.reduce(0, (acc, x) -> acc + x)` compiles and sums to an `Int`
 * (Colls's generics, via the shadowing), while calling
 * `Iterables::reduce(xs, 0, (acc, x) -> acc + x)` explicitly fails to compile
 * with E0001 ("operator + is not applicable for type Object, Int") because its
 * `acc` is typed `Object`.
 */
class IterablesReduceShadowingSpec extends AbstractShellSpec {

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("extension-call syntax for reduce() on a List[Int] shadows onion.Iterables") {
    it("xs.reduce(0, f) type-checks acc as Int and sums correctly (Colls's generic behavior)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3]
          |    return xs.reduce(0, (acc, x) -> acc + x)
          |  }
          |}
          |""".stripMargin, "IterablesReduceShadow.on", Array())
      assert(Shell.Success(6) == result)
    }

    it("Iterables::reduce(xs, 0, f) fails to compile -- its acc is Object, not Int (E0001)") {
      assert(errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3]
          |    return Iterables::reduce(xs, 0, (acc, x) -> acc + x)
          |  }
          |}
          |""".stripMargin
      ).contains("E0001"))
    }
  }

  describe("docs carry the reduce() shadowing warning in the Iterables Module section") {
    def read(p: String): String =
      java.nio.file.Files.readString(java.nio.file.Path.of(p))

    def section(doc: String, heading: String): String = {
      val lines = doc.linesIterator.toIndexedSeq
      val start = lines.indexWhere(_.contains(heading))
      assert(start >= 0, s"""could not find a "$heading" heading -- the scan has rotted""")
      val rest = lines.drop(start + 1)
      val end = rest.indexWhere(_.startsWith("## "))
      (if (end < 0) rest else rest.take(end)).mkString("\n")
    }

    val enMarkers = Set("reduce", "onion.Colls", "E0001")
    val jaMarkers = Set("reduce", "onion.Colls", "E0001")

    it("docs/reference/stdlib.md's Iterables Module section warns about reduce() shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Iterables Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Iterables Module section is missing reduce()-shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Iterables section warns about reduce() shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Iterables モジュール section is missing reduce()-shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
