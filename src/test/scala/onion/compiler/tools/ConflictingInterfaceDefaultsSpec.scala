package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** A class with two unrelated interface defaults for one method compiled, then threw
  * AbstractMethodError at the call (issue #2835). */
class ConflictingInterfaceDefaultsSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  /** Runs `src` with a `Main` whose `main` returns `expr`. */
  private def run(src: String, expr: String): Shell.Result =
    shell.run(
      src + s"""
         |class Main {
         |public:
         |  static def main(args: String[]): Int { return $expr }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  private val unrelated =
    """
      |interface A { def g(): Int = 1 }
      |interface B { def g(): Int = 2 }
      |""".stripMargin

  describe("a class that inherits unrelated defaults for one method") {
    it("is E0116 at the class") {
      val c = codes(unrelated +
        """
          |class C conforms A, B {
          |public:
          |  def this {}
          |}
          |println(new C().g())
          |""".stripMargin)
      assert(c == Seq(Some("E0116")), s"got: $c")
    }

    it("is E0116 when both defaults arrive through one interface") {
      val c = codes(unrelated +
        """
          |interface AB conforms A, B { }
          |class C conforms AB {
          |public:
          |  def this {}
          |}
          |println(new C().g())
          |""".stripMargin)
      assert(c == Seq(Some("E0116")), s"got: $c")
    }

    it("is E0116 at the concrete subclass of an abstract class that leaves the clash open") {
      val c = codes(unrelated +
        """
          |abstract class P conforms A, B { }
          |class C extends P {
          |public:
          |  def this {}
          |}
          |println(new C().g())
          |""".stripMargin)
      assert(c == Seq(Some("E0116")), s"got: $c")
    }

    it("is E0116 for a record") {
      val c = codes(unrelated +
        """
          |record R(x: Int) conforms A, B
          |println(new R(1).g())
          |""".stripMargin)
      assert(c == Seq(Some("E0116")), s"got: $c")
    }

    it("is not reported for a method with different parameters (an overload)") {
      val r = run(
        """
          |interface A { def g(): Int = 1 }
          |interface B { def g(x: Int): Int = x }
          |class C conforms A, B {
          |public:
          |  def this {}
          |}
          |""".stripMargin, "new C().g() + new C().g(5)")
      assert(r == Shell.Success(6), s"got: $r")
    }
  }

  describe("a class whose defaults are settled") {
    it("runs the class's own override") {
      val r = run(unrelated +
        """
          |class C conforms A, B {
          |public:
          |  def this {}
          |  override def g(): Int = 3
          |}
          |""".stripMargin, "new C().g()")
      assert(r == Shell.Success(3), s"got: $r")
    }

    it("runs a superclass method, which wins over both defaults") {
      val r = run(unrelated +
        """
          |class P { public: def this {} def g(): Int = 3 }
          |class C extends P conforms A, B {
          |public:
          |  def this {}
          |}
          |""".stripMargin, "new C().g()")
      assert(r == Shell.Success(3), s"got: $r")
    }

    it("runs the default of the more specific interface") {
      val r = run(
        """
          |interface A { def g(): Int = 1 }
          |interface B conforms A { def g(): Int = 2 }
          |class C conforms A, B {
          |public:
          |  def this {}
          |}
          |""".stripMargin, "new C().g()")
      assert(r == Shell.Success(2), s"got: $r")
    }

    it("runs the one default when the other interface only declares the method") {
      val r = run(
        """
          |interface A { def g(): Int = 1 }
          |interface B { def g(): Int }
          |class C conforms A, B {
          |public:
          |  def this {}
          |}
          |""".stripMargin, "new C().g()")
      assert(r == Shell.Success(1), s"got: $r")
    }

    it("leaves an abstract class alone") {
      val r = run(unrelated +
        """
          |abstract class P conforms A, B { }
          |class C extends P {
          |public:
          |  def this {}
          |  override def g(): Int = 3
          |}
          |""".stripMargin, "new C().g()")
      assert(r == Shell.Success(3), s"got: $r")
    }
  }
}
