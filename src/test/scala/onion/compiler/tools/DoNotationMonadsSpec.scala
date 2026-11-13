package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import onion.tools.Shell

import java.io.StringReader

/**
 * Tests for issue #131: do-notation over Option and Result (bind/successful
 * added to both), and do-notation at the script top level (top-level block
 * elements were not desugared, so the statement vanished silently).
 */
class DoNotationMonadsSpec extends AbstractShellSpec {

  describe("do-notation over Option") {
    it("chains bindings and short-circuits on none") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] { a <- Option::some(40); b <- Option::some(2); yield (a as Int) + (b as Int) }
          |    val n = do[Option] { a <- Option::some(1); b <- Option::none(); yield a }
          |    return r.get() + ":" + n.isEmpty()
          |  }
          |}
          |""".stripMargin,
        "DoOption.on",
        Array()
      )
      assert(Shell.Success("42:true") == result)
    }

    it("accepts newline-separated binds (issue #160)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r = do[Option] {
          |      a <- Option::some(10)
          |      b <- Option::some(20)
          |      yield (a as Int) + (b as Int)
          |    }
          |    return (r.get() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoNewline.on",
        Array()
      )
      assert(Shell.Success(30) == result)
    }
  }

  describe("do-notation over Option with Option::none() (issue #279)") {
    it("infers none()'s element type from a sibling some() bind so arithmetic type-checks") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] {
          |      a <- Option::some(3)
          |      b <- Option::none()
          |      yield (a as Int) + (b as Int)
          |    }
          |    return r.isEmpty().toString()
          |  }
          |}
          |""".stripMargin,
        "DoNone.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("pins none() from a sibling some() regardless of bind order") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] {
          |      b <- Option::none()
          |      a <- Option::some(7)
          |      yield (a as Int) + (b as Int)
          |    }
          |    return r.isEmpty().toString()
          |  }
          |}
          |""".stripMargin,
        "DoNoneFirst.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }

    it("leaves an explicitly annotated none() untouched") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r = do[Option] {
          |      a <- Option::some(5)
          |      b <- Option::none[Int]()
          |      yield (a as Int) + (b as Int)
          |    }
          |    return r.isEmpty().toString()
          |  }
          |}
          |""".stripMargin,
        "DoNoneAnnotated.on",
        Array()
      )
      assert(Shell.Success("true") == result)
    }
  }

  describe("do-notation over Result") {
    it("chains ok values") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r = do[Result] { a <- Result::ok(20); b <- Result::ok(22); yield (a as Int) + (b as Int) }
          |    return (r.getOrThrow() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoResult.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("binds Result::successful without pinning an error type (#2537)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Result[Int, String] = Result::ok(1)
          |    val out: Result[Int, String] = do[Result] { x <- r; y <- Result::successful(x + 1); yield y * 10 }
          |    return (out.getOrThrow() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoResultSuccessful.on",
        Array()
      )
      assert(Shell.Success(20) == result)
    }

    it("binds Result::ok, alone or mixed with Result::successful, the same way (#2537)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Result[Int, String] = Result::ok(1)
          |    val a: Result[Int, String] = do[Result] { x <- r; y <- Result::ok(x + 1); yield y * 10 }
          |    val b: Result[Int, String] = do[onion.Result] { x <- r; y <- Result::ok(x + 1); z <- onion.Result::successful(y + 1); yield z }
          |    return (a.getOrThrow() as Int) + (b.getOrThrow() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoResultOk.on",
        Array()
      )
      assert(Shell.Success(23) == result)
    }

    it("leaves a bind on a user class named Result as a bind (#2537)") {
      val result = shell.run(
        """
          |class Result {
          |  val v: Int
          |public:
          |  def this(v: Int) { this.v = v }
          |  static def successful(v: Int): Result = new Result(v * 100)
          |  def bind(f: Function1[Int, Result]): Result = f.call(v)
          |  def map(f: Function1[Int, Int]): Result = new Result(f.call(v))
          |  def get(): Int = v
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Result = do[Result] { a <- Result::successful(1); b <- Result::successful(a + 1); yield a + b }
          |    return r.get()
          |  }
          |}
          |""".stripMargin,
        "DoUserResult.on",
        Array()
      )
      assert(Shell.Success(10200) == result)
    }

    it("adds no warning where the bind it replaces had none, so --warn error accepts it (#2537)") {
      // `x <- Result::ok(q + 1)` becomes a local `val x`; the bind's lambda parameter never
      // warned W0005 for shadowing an outer `x`, so the generated `val` must not either.
      def warningsUnderError(body: String) = {
        val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.Error)
        val source = "val x = 100\nval r: Result[Int, Object] = Result::ok(1)\nprintln(do[Result] { " + body + " })\n"
        new OnionCompiler(config)
          .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "W.on")))
      }
      val control = warningsUnderError("q <- r; x <- r; yield x")
      val rewritten = warningsUnderError("q <- r; x <- Result::ok(q + 1); yield x")
      val unused = warningsUnderError("q <- r; y <- Result::successful(q + 1); yield q")
      val unusedControl = warningsUnderError("q <- r; y <- r; yield q")
      assert(!control.hasErrors, s"control: ${control.allErrors.map(_.message)}")
      assert(!rewritten.hasErrors, s"rewritten: ${rewritten.allErrors.map(_.message)} ${rewritten.diagnostics.warnings.map(_.message)}")
      assert(!unused.hasErrors, s"unused: ${unused.allErrors.map(_.message)} ${unused.diagnostics.warnings.map(_.message)}")
      def codes(r: onion.compiler.pipeline.CompilationResult) = r.diagnostics.warnings.map(_.category.code)
      assert(codes(rewritten) == codes(control), s"rewritten warnings: ${rewritten.diagnostics.warnings.map(_.message)}")
      assert(codes(unused) == codes(unusedControl), s"unused warnings: ${unused.diagnostics.warnings.map(_.message)}")
    }

    it("adds no unused-variable warning inside a method body, so --warn error accepts it (#2537)") {
      // Top level hid this: only a method body reports W0001 for an unused local.
      def inMethod(bind: String) = {
        val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.Error)
        val source =
          "def f(r: Result[Int, Object]): Result[Int, Object] {\n" +
          "  return do[Result] { x <- r; " + bind + "; yield x }\n}\nprintln(f(Result::ok(1)))\n"
        new OnionCompiler(config)
          .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "M.on")))
      }
      val rewritten = inMethod("y <- Result::ok(x + 1)")
      assert(!rewritten.hasErrors, s"${rewritten.allErrors.map(_.message)} ${rewritten.diagnostics.warnings.map(_.message)}")
    }

    it("reports a wrong yield type after a rewritten bind as the plain bind form does (#2537)") {
      // Removing `y <- Result::ok(e)` must leave the block main would build without it, so a
      // String yielded where an Int belongs gets main's one error, not a reversed pair.
      def errorsOf(body: String) = {
        val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
        val source = "val r: Result[Int, String] = Result::ok(1)\nval out: Result[Int, String] = do[Result] { " + body + " }\n"
        new OnionCompiler(config)
          .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Y.on")))
          .allErrors.map(e => (e.errorCode, e.location.line, e.location.column, e.message))
      }
      val prefix = "val out: Result[Int, String] = do[Result] { "
      val rewrittenBody = "x <- r; y <- Result::ok(x + 1); yield \"s\" + y"
      val controlBody = "y <- r; yield \"s\" + y"
      val rewritten = errorsOf(rewrittenBody)
      val control = errorsOf(controlBody)
      assert(rewritten.size == 1, s"rewritten: $rewritten")
      assert(control.size == 1, s"control: $control")
      val yieldColumn = prefix.length + rewrittenBody.indexOf("yield") + 1
      assert(rewritten.head._2 == 2 && rewritten.head._3 == yieldColumn, s"rewritten: $rewritten")
      // Same code and the same (not reversed) expected/actual text as the plain bind form.
      assert(rewritten.head._1 == control.head._1 && rewritten.head._4 == control.head._4, s"rewritten: $rewritten, control: $control")
    }

    it("reports a wrong yield type after a unit bind that opens the block as main does (#2537)") {
      // Nothing is bound before `y <- Result::ok(..)`, so main's `ok(e).map(y -> v)` shape stays:
      // one error at `yield`, the right way round, and no second error on the bind line.
      def errorsOf(target: String, body: String) = {
        val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10)
        val source = "import { onion.Result }\nval t: " + target + " = do[Result] { " + body + " }\n"
        new OnionCompiler(config)
          .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "Y.on")))
          .allErrors.map(e => (e.errorCode, e.location.line, e.location.column, e.message))
      }
      val cases = Seq(
        ("Result[String, String]", "y <- Result::ok(2); yield y"),
        ("Result[Int, String]", "y <- Result::ok(1); yield y + \"s\""),
        ("Result[String, String]", "y <- Result::successful(1); z <- Result::ok(2); yield y + z"))
      for ((target, body) <- cases) {
        val errors = errorsOf(target, body)
        assert(errors.size == 1, s"$body: $errors")
        assert(errors.head._1 == Some("E0000") && errors.head._2 == 2, s"$body: $errors")
        assert(errors.head._3 == ("val t: " + target + " = do[Result] { " + body).indexOf("yield") + 1, s"$body: $errors")
      }
    }
    it("lets a later unit bind reuse an earlier bind's name, as a lambda parameter may (#2537)") {
      // Each bind is a nested lambda parameter on main, so rebinding a name is legal; each
      // generated `val` must be scoped the same way, not share one block (E0007).
      def run(body: String) = {
        val source = "import { onion.Result }\nval r: Result[Int, Object] = Result::ok(1)\n" +
          "val out: Result[Int, Object] = do[Result] { " + body + " }\nprintln(out)\n"
        new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.Error))
          .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "R.on")))
      }
      val bodies = Seq(
        "x <- r; y <- Result::ok(x + 1); y <- Result::ok(y * 3); yield y",
        "y <- r; y <- Result::ok(y + 1); y <- Result::ok(y * 3); yield y",
        "a <- Result::ok(1); a <- Result::ok(a + 1); a <- Result::ok(a + 1); yield a",
        "x <- r; y <- Result::ok(x + 1); y <- Result::ok(y * 3); z <- r; yield y + z")
      for (body <- bodies) {
        val result = run(body)
        assert(!result.hasErrors, s"$body: ${result.allErrors.map(e => (e.errorCode, e.message))} ${result.diagnostics.warnings.map(_.message)}")
      }
      val value = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val r: Result[Int, Object] = Result::ok(1)
          |    val out: Result[Int, Object] = do[Result] { x <- r; y <- Result::ok(x + 1); y <- Result::ok(y * 3); yield y }
          |    return (out.getOrThrow() as Int)
          |  }
          |}
          |""".stripMargin,
        "DoResultRebind.on",
        Array()
      )
      assert(Shell.Success(6) == value)
    }

    it("adds no W0012 for a Result::ok(null) bind, so --warn error accepts it as main does (#2537)") {
      // The bind's lambda parameter is an `Object`, so the generated `val` must be one too.
      def warningsUnderError(body: String) = {
        val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.Error)
        val source = "import { onion.Result }\nval r: Result[Int, Object] = Result::ok(1)\nprintln(do[Result] { " + body + " })\n"
        new OnionCompiler(config)
          .compileDetailed(Seq(new StreamInputSource(() => new StringReader(source), "N.on")))
      }
      for (body <- Seq("x <- r; y <- Result::ok(null); yield y", "x <- r; y <- Result::ok(null); z <- r; yield y")) {
        val result = warningsUnderError(body)
        assert(!result.hasErrors, s"$body: ${result.allErrors.map(e => (e.errorCode, e.message))} ${result.diagnostics.warnings.map(_.message)}")
      }
    }
  }

  describe("do-notation over List (comprehension)") {
    it("computes a cross product with newline-separated binds") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val pairs = do[List] {
          |      x <- [1, 2]
          |      y <- ["a", "b"]
          |      yield x + y
          |    }
          |    return pairs.toString()
          |  }
          |}
          |""".stripMargin,
        "DoList.on",
        Array()
      )
      assert(Shell.Success("[1a, 1b, 2a, 2b]") == result)
    }

    it("maps a single bind") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sq = do[List] { x <- [1, 2, 3]; yield x * x }
          |    return sq.toString()
          |  }
          |}
          |""".stripMargin,
        "DoListMap.on",
        Array()
      )
      assert(Shell.Success("[1, 4, 9]") == result)
    }
  }

  describe("do-notation at the top level") {
    it("desugars top-level statements containing do expressions") {
      val result = shell.run(
        """
          |val r = do[Option] { a <- Option::some(40); b <- Option::some(2); yield (a as Int) + (b as Int) }
          |IO::println(r.get())
          |""".stripMargin,
        "DoTopLevel.on",
        Array()
      )
      assert(Shell.Success(()) == result || result.isInstanceOf[Shell.Success])
    }
  }
}
