package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** An `Int?` is a boxed `Integer`; it never becomes a `Long?`, so it is a compile error, not a ClassCastException (#2730). */
class NullablePrimitiveBoxMismatchSpec extends AbstractShellSpec {
  private def program(body: String): String =
    s"""
       |record N(a: Int, b: Long?)
       |class Test {
       |public:
       |  static def main(args: String[]): Int {
       |    $body
       |    return 0
       |  }
       |}
       |""".stripMargin

  private def run(body: String): Shell.Result = shell.run(program(body), "None", Array())

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runOutput(src: String): List[String] = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val saved = System.out
    val result =
      try {
        System.setOut(ps)
        Console.withOut(ps)(shell.run(src, "None", Array()))
      } finally System.setOut(saved)
    val out = new String(buf.toByteArray, "UTF-8")
    assert(result.isInstanceOf[Shell.Success], s"$result\n$out")
    out.linesIterator.toList
  }

  describe("a nullable primitive of a different type") {
    it("`val b: Long? = j` with `j: Int?` is rejected at compile time") {
      assert(Shell.Failure(-1) == run("val j: Int? = 3\n    val b: Long? = j"))
    }
    it("a constructor argument `new N(1, x)` with `x: Int?` is rejected") {
      assert(Shell.Failure(-1) == run("val x: Int? = 7\n    val n = new N(1, x)"))
    }
    it("a list element `[j]` for a `List[Long?]` is rejected") {
      assert(Shell.Failure(-1) == run("val j: Int? = 4\n    val ys: List[Long?] = [j]"))
    }
    it("the same primitive still fits") {
      assert(Shell.Success(0) == run("val j: Long? = 3L\n    val b: Long? = j"))
    }
  }

  describe("the diagnostic for a nullable target") {
    // The target takes null, so null is not the problem: E0000, not E0070 with a `!!` hint.
    Seq(
      "val j: Int? = 3\n    val b: Long? = j",
      "val x: Int? = 3\n    var y: Long? = null\n    y = x",
      "val s: Short? = null\n    val i: Int? = s",
      "val x: Int? = 3\n    val n = new N(1, 2L)\n    val m = n.copy(b = x)"
    ).foreach { body =>
      it(s"reports E0000, not E0070: ${body.replace("\n    ", "; ")}") {
        val codes = errorCodes(program(body))
        assert(codes.nonEmpty, "expected a compile error")
        assert(!codes.contains("E0070"), s"must not report the null-safety error: $codes")
      }
    }
    it("still reports E0070 for a nullable value into a non-null slot") {
      assert(errorCodes(program("val j: Int? = 3\n    val b: Long = j")).contains("E0070"))
    }
  }

  describe("a cast into a nullable primitive") {
    it("`(i as Long?)` with `i: Int` converts and boxes, so it prints 5") {
      val src =
        """
          |val i: Int = 5
          |val a = (i as Long?)
          |println(a)
          |val c: Char = 'A'
          |println((c as Int?))
          |println((i as Int?))
          |""".stripMargin
      assert(runOutput(src) == List("5", "65", "5"))
    }
    it("`(k as Long?)` with `k: JInteger` is rejected; `(k as Long)` and `(k as Int?)` still compile") {
      def withK(cast: String) =
        s"""
           |import { java.lang.Integer as JInteger; }
           |val k: JInteger = JInteger::valueOf(7)
           |println($cast)
           |""".stripMargin
      assert(errorCodes(withK("(k as Long?)")).contains("E0000"))
      assert(runOutput(withK("(k as Long)")) == List("7"))
      assert(runOutput(withK("(k as Int?)")) == List("7"))
    }
  }

  describe("overloads on two nullable primitives") {
    // `f(Int?)` stays more specific than `f(Long?)`, as on main before #2730.
    val src =
      """
        |class K {
        |public:
        |  static def f(x: Long?): String = "long:" + x
        |  static def f(x: Int?): String = "int:" + x
        |  static def g(x: Double?): String = "double:" + x
        |  static def g(x: Int?): String = "int:" + x
        |}
        |def f(x: Long?): String = "long:" + x
        |def f(x: Int?): String = "int:" + x
        |val j: Int? = null
        |println(K::f(3))
        |println(K::f(j))
        |println(K::g(3))
        |println(f(3))
        |println(K::f(4L))
        |""".stripMargin
    it("an `Int` or `Int?` argument picks `f(Int?)`, a `Long` picks `f(Long?)`") {
      assert(errorCodes(src).isEmpty, s"errors: ${errorCodes(src)}")
      assert(runOutput(src) == List("int:3", "int:null", "int:3", "int:3", "long:4"))
    }
  }
}
