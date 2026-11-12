package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * A `? super T` argument limits `T` from above, and a plain `T` argument fixes it. When the
 * limit is a type variable, the fixed value must be one of that variable's own values (#2695).
 * `Collections::fill(x: List[T], v: Object)` inside `G[T]` used to compile, then throw a
 * ClassCastException when `x[0]` was read back as a `T`; Java rejects the call, and so did
 * Onion already for a concrete `List[String]`.
 */
class WildcardSuperValueSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def inG(sig: String, body: String): String =
    s"""import { java.util.* }
       |class G[T] {
       |public:
       |  def f($sig): void { $body }
       |}
       |""".stripMargin

  describe("Collections::fill into a type-variable list") {
    it("rejects an Object value for a List[T] (the issue's repro)") {
      assert(errorCodes(inG("x: List[T], v: Object", "Collections::fill(x, v)")) == Seq("E0005"))
    }

    it("rejects a List[Object] value for a List[List[T]]") {
      assert(errorCodes(inG("x: List[List[T]], v: List[Object]", "Collections::fill(x, v)")) == Seq("E0005"))
    }

    it("rejects an Object value at the top level too") {
      val src =
        """import { java.util.* }
          |def a[T](x: List[T], v: Object): void { Collections::fill(x, v) }
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0005"))
    }

    it("still accepts a T value") {
      assert(errorCodes(inG("x: List[T], v: T", "Collections::fill(x, v)")).isEmpty)
    }

    it("still accepts a T? value for a List[T?]") {
      assert(errorCodes(inG("x: List[T?], v: T?", "Collections::fill(x, v)")).isEmpty)
    }

    it("still accepts a List[T] value for a List[List[T]]") {
      assert(errorCodes(inG("x: List[List[T]], v: List[T]", "Collections::fill(x, v)")).isEmpty)
    }

    it("still accepts a T value for a List[Object]") {
      assert(errorCodes(inG("x: List[Object], v: T", "Collections::fill(x, v)")).isEmpty)
    }

    it("still accepts an Object value for a concrete List[Object]") {
      assert(errorCodes(inG("x: List[Object], v: Object", "Collections::fill(x, v)")).isEmpty)
    }
  }

  describe("the run the issue reported") {
    it("no longer reaches a ClassCastException: the call is a compile error") {
      val src =
        """import { java.util.* }
          |class G[T] {
          |public:
          |  def f(x: List[T], v: Object): void { Collections::fill(x, v) }
          |}
          |def main(args: String[]): Int {
          |  val x = new ArrayList[String](); x.add("a")
          |  new G[String]().f(x, 42)
          |  val s: String = x[0]
          |  println(s.length())
          |  return 0
          |}
          |""".stripMargin
      assert(errorCodes(src).contains("E0005"))
    }
  }

  describe("a type that only the expected result fixes") {
    // `val o: Object = Collections::max(x, c)` fixes T = Object from the target alone; no
    // argument passes an Object into the list, so the `? super T` limit has nothing to refuse.
    val maxMin =
      """import { java.util.* }
        |class G[T] {
        |public:
        |  def f(x: List[T], c: Comparator[T]): Object {
        |    val o: Object = Collections::max(x, c)
        |    return o
        |  }
        |  def g(x: List[T], c: Comparator[T]): Object = Collections::min(x, c)
        |}
        |""".stripMargin

    it("accepts Collections::max/min with a Comparator[T] assigned to Object") {
      assert(errorCodes(maxMin).isEmpty)
    }

    it("accepts the same call in a generic top-level function") {
      val src =
        """import { java.util.* }
          |def mx[T](x: List[T], c: Comparator[T]): Object = Collections::max(x, c)
          |""".stripMargin
      assert(errorCodes(src).isEmpty)
    }

    it("runs and returns the maximum and the minimum") {
      val src = maxMin +
        """class MaxMinMain {
          |public:
          |  static def main(args: String[]): String {
          |    val x = new ArrayList[String](); x.add("a"); x.add("b")
          |    val c: Comparator[String] = (a, b) -> a.compareTo(b)
          |    return "" + new G[String]().f(x, c) + new G[String]().g(x, c)
          |  }
          |}
          |""".stripMargin
      assert(Shell.Success("ba") == shell.run(src, "MaxMinMain.on", Array()))
    }
  }
}
