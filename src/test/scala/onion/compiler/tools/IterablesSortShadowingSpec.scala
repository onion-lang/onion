package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `java.util.List` already declares an instance method `sort(Comparator)`
 * (a default method since Java 8), and an instance method always wins over
 * an extension method of the same name, so `xs.sort(comparator)` never
 * reaches `onion.Iterables::sort(List, Comparator)` at all -- even though
 * docs/reference/stdlib.md (and its Japanese translation) list
 * `xs.sort() / xs.sort(comparator)` together as plain `Iterables` chaining
 * examples without calling out the shadowing for the two-arg form.
 *
 * Unlike the runtime-only edge cases documented for `map`/`filter`/`take`/
 * `drop`/`reverse` elsewhere in this section, this one is visible at
 * *compile time*: native `List.sort` mutates its receiver in place and
 * returns `void`, while `Iterables::sort` leaves the receiver untouched and
 * returns a new sorted `List`. So `xs.sort(comparator)` compiles fine as an
 * unused statement (destructively sorting `xs`), but fails to compile with
 * E0000 the moment its result is used as a value. The no-arg form is
 * unaffected: `List` declares no no-arg `sort()`, so `xs.sort()` still
 * reaches `Iterables::sort(List)` and returns a new sorted copy.
 */
class IterablesSortShadowingSpec extends AbstractShellSpec {

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("extension-call syntax for sort(comparator) on a List[Int] shadows onion.Iterables") {
    it("xs.sort(comparator) as a bare statement mutates xs in place (native List.sort)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [3, 1, 2]
          |    xs.sort((a, b) -> a - b)
          |    return xs[0] * 100 + xs[1] * 10 + xs[2]
          |  }
          |}
          |""".stripMargin, "IterablesSortShadowStatement.on", Array())
      assert(Shell.Success(123) == result)
    }

    it("val ys = xs.sort(comparator) fails to compile -- native List.sort returns void (E0000)") {
      assert(errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [3, 1, 2]
          |    val ys = xs.sort((a, b) -> a - b)
          |    return 0
          |  }
          |}
          |""".stripMargin
      ).contains("E0000"))
    }

    it("Iterables::sort(xs, comparator) returns a new sorted copy, leaving xs untouched") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [3, 1, 2]
          |    val ys = Iterables::sort(xs, (a, b) -> a - b)
          |    return xs[0] * 1000 + ys[0] * 100 + ys[1] * 10 + ys[2]
          |  }
          |}
          |""".stripMargin, "IterablesSortStaticCall.on", Array())
      assert(Shell.Success(3123) == result)
    }

    it("xs.sort() (no-arg) is unaffected by the shadowing and returns a new sorted copy") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [3, 1, 2]
          |    val ys = xs.sort()
          |    return xs[0] * 1000 + ys[0] * 100 + ys[1] * 10 + ys[2]
          |  }
          |}
          |""".stripMargin, "IterablesSortNoArg.on", Array())
      assert(Shell.Success(3123) == result)
    }
  }

  describe("docs carry the sort(comparator) shadowing warning in the Iterables Module section") {
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

    val enMarkers = Set("sort", "List.sort", "E0000")
    val jaMarkers = Set("sort", "List.sort", "E0000")

    it("docs/reference/stdlib.md's Iterables Module section warns about sort(comparator) shadowing") {
      val doc = section(read("docs/reference/stdlib.md"), "## Iterables Module")
      val missing = enMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/reference/stdlib.md's Iterables Module section is missing sort()-shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }

    it("docs/ja/reference/stdlib.md's Iterables section warns about sort(comparator) shadowing") {
      val doc = section(read("docs/ja/reference/stdlib.md"), "## Iterables モジュール")
      val missing = jaMarkers.filterNot(doc.contains)
      assert(missing.isEmpty,
        s"docs/ja/reference/stdlib.md's Iterables モジュール section is missing sort()-shadowing-warning markers: ${missing.toSeq.sorted.mkString(", ")}")
    }
  }
}
