package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Tests for W0015 (issue #318): a boxed platform value (e.g. Json::getInt's
 * Integer, null when the key is missing) implicitly unboxed to a non-null
 * primitive warns, since the unboxing throws a raw NullPointerException at
 * runtime rather than surfacing the absence.
 */
class PlatformUnboxingWarningSpec extends AnyFunSpec {

  private def compileWarnings(source: String, level: WarningLevel = WarningLevel.On) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = level)
    new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
  }

  describe("W0015 platform unboxing") {
    it("warns when Json::getInt (boxed Integer) is assigned to a non-null Int") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val obj = Json::parse("{}")
          |    val n: Int = Json::getInt(obj, "missing")
          |    return n
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("does not warn when the boxed value is the left operand of ?: with a non-null fallback") {
      val result = compileWarnings(
        """
          |def g(x: Int?): Int = x ?: 0
          |def f(s: String?): Int = s?.length ?: -1
          |println(g(null))
          |println(f("abc"))
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"unexpected W0015: ${w15.map(_.message)}")
    }

    it("does not warn when directly calling a FunctionN value whose return type is a non-nullable primitive") {
      // f's static type is onion.Function1[Integer, Integer] (Int boxed, not
      // Int?): every implementation must autobox a genuine Int on return, so
      // the result of calling it can never actually be null, unlike
      // Colls::fold's truly generic, call-site-bound type variable R.
      val result = compileWarnings(
        """
          |val f: Int -> Int = (x) -> x * 2
          |val r: Int = f(3)
          |val isEven: Int -> Boolean = (x) -> x % 2 == 0
          |val b: Boolean = isEven(4)
          |def apply2(g: (Int, Int) -> Int, y: Int): Int = g(y, y)
          |println(r)
          |println(b)
          |println(apply2((x, y) -> x + y, 5))
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"unexpected W0015: ${w15.map(_.message)}")
    }

    it("does not warn when an Onion-typed List[Int]/Option[Int]/Result[Int, E] yields its non-null Int (#3071)") {
      val result = compileWarnings(
        """
          |import { onion.Option; onion.Result; }
          |val xs: List[Int] = [1, 2]
          |val d: Int = xs[0]
          |val o: Option[Int] = Option::some(1)
          |val c: Int = o.get()
          |val r: Result[Int, String] = Result::ok(20)
          |val a: Int = r.getOrElse(0)
          |println(d + c + a)
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"unexpected W0015: ${w15.map(_.message)}")
    }

    it("still warns for a Map lookup and for a nullable type argument (#3071)") {
      val result = compileWarnings(
        """
          |val m: Map[String, Int] = ["a": 1]
          |val v: Int = m["a"]
          |val ys: List[Int?] = [1, null]
          |val w: Int = ys[0]!!
          |println(v + w)
          |""".stripMargin)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length >= 1, s"expected W0015 for the Map lookup, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("still warns for a Java read that returns null to mean nothing is there (#3071)") {
      // Only the allowlisted reads (List.get, Iterator.next, Map.Entry, Option,
      // Result) are non-null by their type argument; these return null by
      // contract, so their type variable stays a platform type.
      val cases = Seq(
        "val q: ArrayDeque[Int] = new ArrayDeque[Int]()\nval x: Int = q.poll()",
        "val q: Deque[Int] = new ArrayDeque[Int]()\nval x: Int = q.peekFirst()",
        "val t: TreeSet[Int] = new TreeSet[Int]()\nval x: Int = t.floor(5)",
        "val a: AtomicReference[Int] = new AtomicReference[Int]()\nval x: Int = a.get()",
        "val l: ThreadLocal[Int] = new ThreadLocal[Int]()\nval x: Int = l.get()",
        "val d: Dictionary[String, Int] = new Hashtable[String, Int]()\nval x: Int = d.get(\"a\")"
      )
      cases.foreach { body =>
        val result = compileWarnings(
          s"import { java.util.*; java.util.concurrent.atomic.AtomicReference; }\n$body\nprintln(x)\n")
        assert(!result.hasErrors, s"unexpected errors for: $body")
        val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
        assert(w15.length == 1, s"expected 1 W0015 for: $body, got: ${result.diagnostics.warnings.map(_.message)}")
      }
    }

    it("does not warn for Iterator.next or a Map entry's value with a non-null type argument (#3071)") {
      val result = compileWarnings(
        """
          |import { java.util.*; }
          |val xs: List[Int] = [1, 2]
          |val it: Iterator[Int] = xs.iterator()
          |val a: Int = it.next()
          |val m: Map[String, Int] = ["a": 1]
          |val e: Map.Entry[String, Int] = m.entrySet().iterator().next()
          |val b: Int = e.getValue()
          |val al: ArrayList[Int] = new ArrayList[Int]()
          |al.add(3)
          |val c: Int = al.get(0)
          |println(a + b + c)
          |""".stripMargin)
      assert(!result.hasErrors, s"${result.diagnostics}")
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.isEmpty, s"unexpected W0015: ${w15.map(_.message)}")
    }

    it("does not warn for the defaulted accessor (already primitive)") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val obj = Json::parse("{}")
          |    val n: Int = Json::getIntOr(obj, "missing", 0)
          |    return n
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      assert(result.diagnostics.warnings.filter(_.category.code == "W0015").isEmpty)
    }

    it("warns for List::fold's accumulator even when every argument is a non-null Int") {
      // onion.Colls::fold<T, R> is a Java generic method: its declared return
      // type is the type variable R, a boxed reference with no Onion-tracked
      // nullability, regardless of what the call site's own arguments pin R
      // to. So the single most idiomatic numeric fold -- a literal `0` zero
      // and an Int-returning combiner -- still unboxes through this warning
      // (see docs/reference/stdlib.md's Colls Module section).
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3]
          |    val total: Int = xs.fold(0) { a, b -> a + b }
          |    return total
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("warns for List::reduce's 3-arg accumulator the same way, despite its concretely-typed signature") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs: List[Int] = [1, 2, 3]
          |    val total: Int = xs.reduce(0, (acc, x) -> acc + x)
          |    return total
          |  }
          |}
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
    }

    it("names the source file even when it is the very first warning of the compilation (#2238)") {
      val result = compileWarnings(
        """
          |val obj = Json::parse("{}")
          |val n: Int = Json::getInt(obj, "missing")
          |""".stripMargin)
      assert(!result.hasErrors)
      val w15 = result.diagnostics.warnings.filter(_.category.code == "W0015")
      assert(w15.length == 1, s"expected 1 W0015, got: ${result.diagnostics.warnings.map(_.message)}")
      assert(w15.head.sourceFile == "W.on", s"expected sourceFile 'W.on', got '${w15.head.sourceFile}'")
    }

    it("fails compilation under warnings-as-errors") {
      val result = compileWarnings(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val obj = Json::parse("{}")
          |    val n: Int = Json::getInt(obj, "missing")
          |    return n
          |  }
          |}
          |""".stripMargin,
        WarningLevel.Error)
      assert(result.hasErrors)
    }
  }
}
