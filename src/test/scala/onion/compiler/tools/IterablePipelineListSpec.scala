package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * #2496: `map`/`filter` on an `Iterable` that is neither a `List` nor a `Set` (a range,
 * `map.values()`, a Java `Iterable`) used to be typed `Iterable[T]`, so `.size`, `[i]` and
 * `.take(n)` on the result did not compile, though the value was already a list. They are
 * typed `List[T]` now.
 */
class IterablePipelineListSpec extends AbstractShellSpec {

  private def run(body: String): Shell.Result =
    shell.run(
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n",
      "IterablePipelineList.on",
      Array())

  describe("map/filter on a non-List Iterable return a List") {
    it("(1..10).filter { ... } has a size and an index") {
      assert(run(
        """
          |val evens = (1..10).filter { x -> x % 2 == 0 }
          |return evens.size + ":" + evens[0] + ":" + evens
          |""".stripMargin) == Shell.Success("5:2:[2, 4, 6, 8, 10]"))
    }

    it("(1..5).map { ... } chains into a list pipeline") {
      assert(run(
        """
          |val sq: List[Int] = (1..5).map { x -> x * x }
          |return "" + (1..5).map { x -> x * x }.take(2) + sq.size
          |""".stripMargin) == Shell.Success("[1, 4]5"))
    }

    it("map.values() pipelines give a List") {
      assert(run(
        """
          |val m = ["a": 1, "b": 2, "c": 3]
          |val big: List[Int] = m.values().filter { v -> v > 1 }
          |return "" + big + big.size + m.values().map { v -> v * 10 }[2]
          |""".stripMargin) == Shell.Success("[2, 3]230"))
    }

    it("the result still fits an Iterable slot") {
      assert(run(
        """
          |val it: Iterable[Int] = (1..3).map { x -> x + 1 }
          |val sb = new StringBuilder()
          |foreach x in it { sb.append(x) }
          |return sb.toString()
          |""".stripMargin) == Shell.Success("234"))
    }
  }

  // Breaking (#2496): the narrower result type changes which overload a call picks.
  describe("an overload on both Iterable and List") {
    it("a static method picks the List one") {
      val result = shell.run(
        """class K {
          |public:
          |  static def f(xs: Iterable[Int]): String = "iter"
          |  static def f(xs: List[Int]): String = "list"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String =
          |    K::f((1..3).filter { x -> x > 1 }) + ":" + K::f((1..3).map { x -> x * 2 })
          |}
          |""".stripMargin, "IterablePipelineOverload.on", Array())
      assert(result == Shell.Success("list:list"))
    }

    it("an extension method picks the List one") {
      val result = shell.run(
        """extension Iterable {
          |  def tag(): String = "it"
          |}
          |extension List {
          |  def tag(): String = "list"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = (1..3).filter { x -> x > 1 }.tag()
          |}
          |""".stripMargin, "IterablePipelineExtension.on", Array())
      assert(result == Shell.Success("list"))
    }
  }

  private def errorsOf(code: String): Seq[String] =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code + "\n"), "Test.on"))
    ).allErrors.map(_.message)

  // Breaking (#2496): `var xs = (1..3).map { ... }` is a List now, so a later `xs = someSet`
  // is E0000. The error names the fix in both locales: declare the variable an Iterable.
  describe("a List variable given another Iterable") {
    val hinted = Seq(
      "var xs = (1..3).map { x -> x + 1 }\nxs = new java.util.TreeSet[Int]()" -> "var xs: Iterable[Int] = ...",
      "var ys = (1..3).map { x -> x * 2 }\nys = 5..6" -> "var ys: Iterable[Int] = ...",
      "var vs = [\"a\": 1].values().filter { v -> v > 0 }\nvs = [\"b\": 2].values()" -> "var vs: Iterable[Int] = ...",
      "def f(): void {\n  var xs = [1, 2]\n  xs = new java.util.HashSet[Int]()\n}" -> "var xs: Iterable[Int] = ..."
    )
    hinted.foreach { case (code, fix) =>
      it(s"says to declare it `$fix`: `${code.replace("\n", "; ")}`") {
        val errors = errorsOf(code)
        assert(errors.exists(_.contains(fix)), s"expected the widening hint, got: $errors")
      }
    }

    val unhinted = Seq(
      "var xs = [1, 2]\nxs = [\"a\"]",
      "var xs = [1, 2]\nxs = new java.util.TreeSet[String]()",
      "var s = \"a\"\ns = new java.util.TreeSet[Int]()",
      "var xs: Iterable[Int] = [1]\nxs = \"a\""
    )
    unhinted.foreach { code =>
      it(s"gets no widening hint: `${code.replace("\n", "; ")}`") {
        val errors = errorsOf(code)
        assert(errors.nonEmpty)
        assert(!errors.exists(_.contains(": Iterable[")), s"unexpected widening hint in: $errors")
      }
    }
  }
}
