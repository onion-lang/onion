package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A `history` declared in a subclass binds the methods it inherits without overriding them (#2603).
 * Under `class Up extends Base { history n >= old(n) }`, an inherited `Base.dec` that lowers `n`
 * breaks the history on every `Up`. It is E0109 at the write when the syntax settles it, and W0024
 * when it does not; before, it was silent.
 */
class HistoryInheritedMethodSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], messages: Seq[String], w24: Seq[String])

  private def compile(src: String): Outcome = compileFiles("H.on" -> src)._1

  /** Compiles the files in the order given; also returns each error's file and line. */
  private def compileFiles(files: (String, String)*): (Outcome, Seq[(String, Int)]) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val sources = files.map { case (name, src) => new StreamInputSource(() => new StringReader(src), name) }
    val r = new OnionCompiler(config).compileDetailed(sources)
    (Outcome(r.diagnostics.errors.flatMap(_.errorCode),
      r.diagnostics.errors.map(_.message),
      r.diagnostics.warnings.filter(_.category.code == "W0024").map(_.message)),
      r.diagnostics.errors.map(e => (e.sourceFile, Option(e.location).map(_.line).getOrElse(0))))
  }

  private def program(baseMethod: String, upBody: String = "") =
    s"""class Base {
       |protected:
       |  var n: Int
       |public:
       |  def this { n = 10 }
       |  $baseMethod
       |  def get(): Int = n
       |}
       |class Up extends Base {
       |  history n >= old(n)
       |public:
       |  def inc(): void requires n < 1000 { n = n + 1 }
       |  $upBody
       |}
       |val u = new Up(); u.inc(); println(u.get())""".stripMargin

  it("is E0109 for an inherited method that lowers the field (the issue's repro)") {
    val o = compile(program("def dec(): void { n = n - 1 }"))
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(m => m.contains("Up") && m.contains("Base") && m.contains("dec")), o.toString)
  }

  it("is E0109 for an inherited `n--` and `n -= 2`") {
    assert(compile(program("def dec(): void { n-- }")).errors == Seq("E0109"))
    assert(compile(program("def dec(): void { n -= 2 }")).errors == Seq("E0109"))
  }

  it("is E0109 for an inherited reset to a constant, since `inc` raises the field") {
    val o = compile(program("def reset(): void { n = 0 }"))
    assert(o.errors == Seq("E0109"), o.toString)
  }

  it("accepts the inherited method once the subclass overrides it") {
    val o = compile(program("def dec(): void { n = n - 1 }", "def dec(): void { }"))
    assert(o.errors.isEmpty, o.toString)
  }

  it("accepts an inherited method that only raises or reads the field") {
    val o = compile(program("def bump(): void { n = n + 2 }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.isEmpty, o.toString)
  }

  it("warns W0024 for an inherited write the syntax does not settle") {
    val o = compile(program("def scale(k: Int): void { n = n * k }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.exists(m => m.contains("Base.scale") && m.contains("Up")), o.toString)
  }

  it("reports a history the superclass declares itself once, at the superclass") {
    val src =
      """class Base {
        |  history n >= old(n)
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |}
        |class Up extends Base {
        |public:
        |  def inc(): void { n = n + 1 }
        |}""".stripMargin
    assert(compile(src).errors == Seq("E0109"))
  }

  it("follows an inherited method two levels up, and stops at the nearest override") {
    val src =
      """class A {
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |  def drop(): void { n = n - 5 }
        |}
        |class B extends A {
        |public:
        |  def drop(): void { }
        |}
        |class C extends B {
        |  history n >= old(n)
        |}""".stripMargin
    val o = compile(src)
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(_.contains("dec")) && !o.messages.exists(_.contains("drop")), o.toString)
  }

  // Panel block on #2781 (04:46), finding 1: a private method is never an override.
  it("does not count a private method of the subclass as an override") {
    val o = compile(program("def dec(): void { n = n - 1 }", "private: def dec(): void { }"))
    assert(o.errors == Seq("E0109"), o.toString)
  }

  it("checks an inherited private write, and names the non-private method that reaches it") {
    val o = compile(program("def dec(): void { lower() }\nprivate:\n  def lower(): void { n = n - 1 }\npublic:"))
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(m => m.contains("`dec`") && !m.contains("`lower`")), o.toString)
  }

  it("still finds the private write when the subclass declares a private method of the same name") {
    val o = compile(program("def dec(): void { lower() }\nprivate:\n  def lower(): void { n = n - 1 }\npublic:",
      "private: def lower(): void { }"))
    assert(o.errors == Seq("E0109"), o.toString)
  }

  it("does not report a private write that no inherited method reaches") {
    val o = compile(program("def dec(): void { lower() }\nprivate:\n  def lower(): void { n = n - 1 }\npublic:",
      "def dec(): void { }"))
    assert(o.errors.isEmpty, o.toString)
  }

  // Finding 2: the error names the file that holds the write, in either order.
  it("reports the inherited write in the superclass's file, whatever the file order") {
    val base =
      """class Base {
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |}""".stripMargin
    val up =
      """class Up extends Base {
        |  history n >= old(n)
        |public:
        |  def inc(): void { n = n + 1 }
        |}""".stripMargin
    for (order <- Seq(Seq("mf/Base.on" -> base, "mf/Up.on" -> up), Seq("mf/Up.on" -> up, "mf/Base.on" -> base))) {
      val (o, where) = compileFiles(order*)
      assert(o.errors == Seq("E0109"), o.toString)
      assert(where == Seq(("mf/Base.on", 6)), s"$order: $where")
    }
  }

  it("reports a class's own write in its own file, whatever the file order") {
    val base =
      """class Base {
        |  history n >= old(n)
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |}""".stripMargin
    val other = "class Other {\npublic:\n  def f(): Int = 1\n}"
    for (order <- Seq(Seq("a/Base.on" -> base, "a/Other.on" -> other), Seq("a/Other.on" -> other, "a/Base.on" -> base))) {
      val (o, where) = compileFiles(order*)
      assert(o.errors == Seq("E0109"), o.toString)
      assert(where == Seq(("a/Base.on", 7)), s"$order: $where")
    }
  }

  // Finding 3: a write binds the history only when it writes the field the history names.
  it("accepts an inherited write to a superclass field the subclass shadows") {
    val src =
      """class Base {
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |  def baseN(): Int = n
        |}
        |class Up extends Base {
        |  var n: Int
        |  history n >= old(n)
        |public:
        |  def this { n = 0 }
        |  def inc(): void requires n < 1000 { n = n + 1 }
        |  def upN(): Int = n
        |}
        |val u = new Up(); u.inc(); u.dec(); println("" + u.upN() + " " + u.baseN())""".stripMargin
    val o = compile(src)
    assert(o.errors.isEmpty, o.toString)
  }

  // Finding 4: the same history in both classes is reported once.
  it("reports a history that both classes declare once") {
    val src =
      """class Base {
        |  history n >= old(n)
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |}
        |class Up extends Base {
        |  history n >= old(n)
        |public:
        |  def inc(): void { n = n + 1 }
        |}""".stripMargin
    val o = compile(src)
    assert(o.errors == Seq("E0109"), o.toString)
    assert(!o.messages.exists(_.contains("inherits")), o.toString)
  }

  it("reports a history declared in a middle class once, at the class that declares it") {
    val src =
      """class A {
        |protected:
        |  var n: Int
        |public:
        |  def this { n = 10 }
        |  def dec(): void { n = n - 1 }
        |}
        |class B extends A {
        |  history n >= old(n)
        |}
        |class C extends B {
        |}""".stripMargin
    val o = compile(src)
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(m => m.contains("`B`") && !m.contains("`C`")), o.toString)
  }

  // #2782: an override that delegates to `super` runs the superclass's body on the subclass.
  it("is E0109 for an override that calls a lowering `super` method (the #2782 repro)") {
    val o = compile(program("def dec(): void { n = n - 1 }", "override def dec(): void { super.dec() }"))
    assert(o.errors == Seq("E0109"), o.toString)
    assert(o.messages.exists(m => m.contains("super.dec") && m.contains("`Base`") && !m.contains("without overriding")), o.toString)
  }

  it("is E0109 when any method of the subclass calls the lowering `super` method") {
    val o = compile(program("def dec(): void { n = n - 1 }", "override def dec(): void { }\n  def twice(): void { super.dec(); super.dec() }"))
    assert(o.errors == Seq("E0109"), o.toString)
  }

  it("accepts an override that calls a `super` method that only raises the field") {
    val o = compile(program("def bump(): void { n = n + 2 }", "override def bump(): void { super.bump() }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.isEmpty, o.toString)
  }

  it("warns W0024 for a `super` method whose write the syntax does not settle, without saying it is not overridden") {
    val o = compile(program("def scale(k: Int): void { n = n * k }", "override def scale(k: Int): void { super.scale(k) }"))
    assert(o.errors.isEmpty, o.toString)
    assert(o.w24.exists(m => m.contains("Base.scale") && m.contains("super.scale") && !m.contains("without overriding")), o.toString)
  }

  it("follows a `super` call to a private write the superclass method reaches") {
    val o = compile(program("def dec(): void { lower() }\nprivate:\n  def lower(): void { n = n - 1 }\npublic:",
      "override def dec(): void { super.dec() }"))
    assert(o.errors == Seq("E0109"), o.toString)
  }

  it("follows `super` through a middle class, both from the subclass and from an inherited override") {
    def src(mid: String, up: String) =
      s"""class A {
         |protected:
         |  var n: Int
         |public:
         |  def this { n = 10 }
         |  def dec(): void { n = n - 1 }
         |}
         |class B extends A {
         |public:
         |  $mid
         |}
         |class C extends B {
         |  history n >= old(n)
         |public:
         |  $up
         |}""".stripMargin
    // B does not declare `dec`, so C's `super.dec()` runs A's.
    assert(compile(src("", "override def dec(): void { super.dec() }")).errors == Seq("E0109"))
    // C inherits B's override, which delegates to A's.
    assert(compile(src("override def dec(): void { super.dec() }", "")).errors == Seq("E0109"))
    // B's override does not delegate, so nothing lowers `n`.
    assert(compile(src("override def dec(): void { }", "override def dec(): void { super.dec() }")).errors.isEmpty)
  }

  // Panel block on #2870: `super.set(x)` is matched to the overload it resolves to, not to the
  // first method of the same name and arity.
  describe("a `super` call to one of two same-arity overloads") {
    def src(first: String, second: String) =
      s"""class Base {
         |protected:
         |  var n: Int
         |  var s: String
         |public:
         |  def this { n = 10; s = "" }
         |  $first
         |  $second
         |  def get(): Int = n
         |}
         |class Up extends Base {
         |  history n >= old(n)
         |public:
         |  override def set(x: Int): void { if n < 1000 { n = n + 1 } }
         |  override def set(x: String): void { super.set(x) }
         |}
         |val u = new Up(); u.set("a"); println(u.get())""".stripMargin
    val lowersInt = "def set(x: Int): void { n = n - 1 }"
    val keepsString = "def set(x: String): void { s = x }"
    val lowersString = "def set(x: String): void { n = n - 1 }"
    val keepsInt = "def set(x: Int): void { s = \"\" + x }"

    it("accepts the call when the overload it runs does not lower the field, in either declaration order") {
      for (o <- Seq(compile(src(lowersInt, keepsString)), compile(src(keepsString, lowersInt)))) {
        assert(o.errors.isEmpty, o.toString)
        assert(o.w24.isEmpty, o.toString)
      }
    }

    it("follows the overload a `super` call inside a lambda runs") {
      def viaLambda(first: String, second: String) =
        src(first, second).replace("{ super.set(x) }", "{ val f: () -> void = () -> super.set(x); f() }")
      val ok = compile(viaLambda(lowersInt, keepsString))
      assert(ok.errors.isEmpty && ok.w24.isEmpty, ok.toString)
      assert(compile(viaLambda(keepsInt, lowersString)).errors == Seq("E0109"))
    }

    it("is E0109 when the overload it runs lowers the field, in either declaration order") {
      assert(compile(src(keepsInt, lowersString)).errors == Seq("E0109"))
      assert(compile(src(lowersString, keepsInt)).errors == Seq("E0109"))
    }
  }
}
