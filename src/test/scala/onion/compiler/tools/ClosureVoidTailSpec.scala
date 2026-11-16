package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A block lambda whose function type returns a concrete value type (`(Int) -> Int`,
 * `() -> String`) and whose last expression is void is E0000 at that expression.
 * It used to compile and return null, which failed later as an NPE (#3044).
 * A void, `Object` or inferred result still accepts a side-effect-only body.
 */
class ClosureVoidTailSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(String, Int)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) =>
        errs.flatMap(e => e.errorCode.map(c => (c, Option(e.location).map(_.line).getOrElse(-1))))
      case _ => Seq.empty
    }
  }

  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a block lambda ending in a void expression") {
    it("is E0000 at the last expression for an Int result") {
      assert(errors("val g: (Int) -> Int = (n) -> {\n  println(\"y\")\n}\nprintln(g(1) + 1)\n") == Seq(("E0000", 2)))
    }

    it("is E0000 for a no-argument Int result") {
      assert(errors("val k: () -> Int = () -> { println(\"k\") }\nprintln(k())\n").map(_._1) == Seq("E0000"))
    }

    it("is E0000 for a String result of an Onion function type") {
      assert(errors("val s: () -> String = () -> { println(\"s\") }\nprintln(s())\n").map(_._1) == Seq("E0000"))
    }

    it("is E0000 after an early return") {
      val src =
        """val h: (Boolean) -> Int = (c) -> {
          |  if c { return 1 }
          |  println("x")
          |}
          |println(h(false))
          |""".stripMargin
      assert(errors(src) == Seq(("E0000", 3)))
    }

    it("still runs as a statement for a void result") {
      val result = run(
        """    var total = 0
          |    val v: (Int) -> void = (n) -> { total = total + n; IO::print("") }
          |    v(3)
          |    val c: java.util.function.Consumer[String] = (s) -> { IO::print(s) }
          |    c.accept("")
          |    return total""".stripMargin)
      assert(Shell.Success(3) == result)
    }

    it("compiles when the tail never completes, or the result is nullable, Void or a Java type variable") {
      val result = run(
        """    var i = 0
          |    val k: () -> Int = () -> {
          |      while true {
          |        i = i + 1
          |        if i > 3 { return i }
          |      }
          |    }
          |    val s: java.util.function.Supplier[Void] = () -> { IO::print("") }
          |    val f: () -> String? = () -> { IO::print("") }
          |    val m: java.util.Map[String, Integer] = new java.util.HashMap[String, Integer]()
          |    if m.computeIfAbsent("a", (k) -> { IO::print("") }) != null { return 0 }
          |    if s.get() != null || f() != null { return 0 }
          |    return k()""".stripMargin)
      assert(Shell.Success(4) == result)
    }

    it("compiles when the tail is a `for` loop with no way out but `return`") {
      val result = run(
        """    var i = 0
          |    val g: () -> Int = () -> {
          |      for ; true; { i++; if i > 2 { return i } }
          |    }
          |    var j = 0
          |    val h: () -> Int = () -> {
          |      for ;; { j++; if j > 3 { return j } }
          |    }
          |    val k: () -> Int = () -> {
          |      for var n: Int = 0; ; n++ { if n > 4 { return n } }
          |    }
          |    return g() * 100 + h() * 10 + k()""".stripMargin)
      assert(Shell.Success(345) == result)
    }

    it("still returns null for an Object or inferred result") {
      val result = run(
        """    val o: () -> Object = () -> { IO::print("") }
          |    val on: () -> Object? = () -> { IO::print("") }
          |    if on() != null { return 0 }
          |    val xs = [1, 2].map { x -> IO::print("") }
          |    val f = Future::async { IO::print("") }
          |    f.await()
          |    return if o() == null && xs[0] == null { xs.size } else { 0 }""".stripMargin)
      assert(Shell.Success(2) == result)
    }
  }
}
