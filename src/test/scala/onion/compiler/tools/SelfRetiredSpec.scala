package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** `self` is retired; `this` is the one spelling of the current instance (RFC #2788, #2737). */
class SelfRetiredSpec extends AbstractShellSpec {
  /** (code, line, column) of each error. */
  private def errors(src: String): Seq[(String, Int, Int)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 100)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) =>
        errs.map(e => (e.errorCode.getOrElse(""), e.location.line, e.location.column))
      case _ => Seq.empty
    }
  }

  describe("self") {
    it("is E0117 at every use, all reported at once") {
      val src =
        """class Counter {
          |  var n: Int
          |public:
          |  def this { self.n = 0 }
          |  def inc(): Counter { self.n += 1; self.n++; return self }
          |  def show(): String = "#{self.n}"
          |  def later(): () -> Int = () -> self.n
          |  def isC(): Boolean = self is Counter
          |}
          |extension String { def twice(): String = self + self }
          |record P(x: Int) { public: def dbl(): Int = self.x() * 2 }
          |println(self)
          |""".stripMargin
      val expected = Seq(
        ("E0117", 4, 14), ("E0117", 5, 24), ("E0117", 5, 37), ("E0117", 5, 54),
        ("E0117", 6, 27), ("E0117", 7, 34), ("E0117", 8, 24),
        ("E0117", 10, 42), ("E0117", 10, 49), ("E0117", 11, 45), ("E0117", 12, 9))
      val found = errors(src)
      assert(found.sortBy(e => (e._2, e._3)) == expected, found.toString)
    }

    it("is E0117 in an argument typed speculatively, such as a constructor's") {
      val src =
        """enum U { case M }
          |record L(v: Double, u: U)
          |extension Double { def m(): L = new L(self, new M()) }
          |""".stripMargin
      assert(errors(src) == Seq(("E0117", 3, 39)))
    }

    it("is E0117, not E0050, in static code") {
      val src =
        """class S {
          |public:
          |  static def s(): Object = self
          |}
          |""".stripMargin
      assert(errors(src) == Seq(("E0117", 3, 28)))
    }

    it("stays a reserved word: a backticked `self` is still a name") {
      val result = shell.run(
        """val `self` = 3
          |class Main {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(0) == result)
    }
  }

  describe("this") {
    it("compiles where self did, with the same result") {
      val result = shell.run(
        """class Counter {
          |  var n: Int
          |public:
          |  def this { this.n = 0 }
          |  def inc(): Counter { this.n += 1; return this }
          |  def show(): String = "#{this.n}"
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String { return new Counter().inc().inc().show() }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("2") == result)
    }
  }
}
