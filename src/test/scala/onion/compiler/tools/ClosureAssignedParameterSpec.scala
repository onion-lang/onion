package onion.compiler.tools

import onion.tools.Shell

/**
 * A parameter that a lambda assigns is one variable with the method, as a captured
 * `var` local is (#2335). It used to be copied into the lambda, so the method kept
 * the old value.
 */
class ClosureAssignedParameterSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |$body
         |  static def main(args: String[]): Int {
         |    return g(1)
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a parameter assigned inside a lambda") {
    it("is seen updated by the method") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val f: () -> Int = () -> { n = n + 10; n }
          |    val inner = f()
          |    return n * 1000 + inner
          |  }""".stripMargin)
      assert(Shell.Success(11011) == result)
    }

    it("is shared both ways with a long parameter and a later method write") {
      val result = run(
        """  static def g(n: Long): Int {
          |    val f: () -> Long = () -> { n = n * 2L; n }
          |    n = n + 1L
          |    val a = f()
          |    n = n + 5L
          |    return (a + n) as Int
          |  }""".stripMargin)
      assert(Shell.Success(4 + 9) == result)
    }

    it("works on an instance method with another parameter") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  def g(label: String, n: Int): String {
          |    val f: () -> Int = () -> { n = n + 10; n }
          |    f()
          |    return label + n
          |  }
          |  static def main(args: String[]): String = new Test().g("n=", 1)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("n=11") == result)
    }

    it("is passed by its argument value to a delegating constructor") {
      val result = shell.run(
        """
          |class B {
          |  var v: Int
          |public:
          |  def this(a: Int, b: Int) { v = a + b }
          |  def this(n: Int): this(n, n * 2) {
          |    val f: () -> Int = () -> { n = n + 1; n }
          |    v = v * 100 + f()
          |  }
          |  def get: Int = v
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int = new B(5).get
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(1506) == result)
    }

    it("leaves a captured but never assigned parameter alone") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val f: () -> Int = () -> n + 1
          |    return f() + n
          |  }""".stripMargin)
      assert(Shell.Success(3) == result)
    }

    it("never shows its hidden argument slot in a \"did you mean\" suggestion") {
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val (o, e) = (System.out, System.err)
      val r =
        try {
          System.setOut(ps); System.setErr(ps)
          Console.withOut(ps) { Console.withErr(ps) {
            shell.run(
              """
                |def g(n: Int, max: Int): Int {
                |  val f: () -> Int = () -> { n = n + 1; max = max - 1; n }
                |  f()
                |  println(maxArg)
                |  return arg
                |}
                |println(g(1, 5))
                |""".stripMargin,
              "None",
              Array())
          } }
        } finally { System.setOut(o); System.setErr(e) }
      val out = new String(buf.toByteArray, "UTF-8")
      assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure, got $r\n$out")
      assert(out.contains("E0002"), out)
      assert(!out.contains("#"), s"hidden slot name leaked:\n$out")
    }
  }

  describe("a lambda parameter assigned inside a nested lambda") {
    it("is seen updated by the outer lambda (#2426)") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val outer: (Int) -> Int = (k) -> {
          |      val inner: () -> Int = () -> { k = k + 10; k }
          |      inner()
          |      k
          |    }
          |    return outer(n)
          |  }""".stripMargin)
      assert(Shell.Success(11) == result)
    }

    it("is shared both ways with a Long parameter and a later write") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val outer: (Long) -> Long = (k) -> {
          |      val inner: () -> Long = () -> { k = k * 2L; k }
          |      k = k + 1L
          |      val a = inner()
          |      k = k + 5L
          |      a + k
          |    }
          |    val r: Long = outer(1L)
          |    return r as Int
          |  }""".stripMargin)
      assert(Shell.Success(4 + 9) == result)
    }

    it("leaves a lambda parameter that is only read alone") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val outer: (Int) -> Int = (k) -> {
          |      val inner: () -> Int = () -> k + 1
          |      inner() + k
          |    }
          |    return outer(n)
          |  }""".stripMargin)
      assert(Shell.Success(3) == result)
    }

    it("does not box a sibling lambda's parameter of the same name") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val xs = [1, 2, 3]
          |    val a = xs.map { k -> val f: () -> Int = () -> { k = k + 1; k }; f() }
          |    val b = xs.map { k -> val f: () -> Int = () -> k * 3; f() }
          |    return a[0] * 100000 + a[2] * 10000 + b[0] * 100 + b[2]
          |  }""".stripMargin)
      assert(Shell.Success(2 * 100000 + 4 * 10000 + 3 * 100 + 9) == result)
    }

    it("does not box a later lambda's parameter after a forEach that assigns it") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val xs = [1, 2, 3]
          |    var total = 0
          |    xs.forEach { x -> val f: () -> Int = () -> { x = x * 2; x }; total = total + f() }
          |    val fs = xs.map { x -> () -> x + 1 }
          |    val h = fs[2]
          |    return total * 100 + h()
          |  }""".stripMargin)
      assert(Shell.Success(12 * 100 + 4) == result)
    }

    it("keeps the inferred result type of a lambda passed to a generic method") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val ys = [1, 2].map { k ->
          |      val inc: () -> void = () -> { k = k + 10 }
          |      inc()
          |      "s" + k
          |    }
          |    var total = 0
          |    foreach v in ys { total = total + v.length() }
          |    return total
          |  }""".stripMargin)
      assert(Shell.Success(6) == result)
    }

    it("keeps the inferred result type when the result does not use the parameter") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val ys = [1, 2].map((k) -> {
          |      val inc: () -> void = () -> { k = k + 10 }
          |      inc()
          |      k * 0 + 7
          |    })
          |    val y: Int = ys[0]
          |    return y + ys[1]
          |  }""".stripMargin)
      assert(Shell.Success(14) == result)
    }

    it("keeps the inferred result type of a lambda with an explicit return") {
      val result = run(
        """  static def g(n: Int): Int {
          |    val ys = [1, 2].map { k ->
          |      val inc: () -> void = () -> { k = k + 10 }
          |      inc()
          |      return "s" + k
          |    }
          |    return ys[0].length() + ys[1].length()
          |  }""".stripMargin)
      assert(Shell.Success(6) == result)
    }
  }
}
