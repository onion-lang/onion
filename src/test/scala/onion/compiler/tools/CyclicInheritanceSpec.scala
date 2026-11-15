package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * E0016 (CYCLIC_INHERITANCE) is reported for both classes and interfaces
 * whose supertype chain loops back on itself, but had no regression
 * coverage at all before this spec.
 */
class CyclicInheritanceSpec extends AbstractShellSpec {
  private def errors(src: String): Seq[(Option[String], String)] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(e => (e.errorCode, e.message))
      case _ => Seq.empty
    }
  }

  describe("cyclic inheritance") {
    it("reports E0016 for a direct class self-cycle") {
      val results = errors(
        """
          |class A extends A {
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0016")))
    }

    it("reports E0016 for an indirect class cycle") {
      val results = errors(
        """
          |class A extends B {
          |}
          |class B extends A {
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0016")))
    }

    it("reports E0016 for an interface cycle") {
      val results = errors(
        """
          |interface I conforms J {
          |}
          |interface J conforms I {
          |}
          |""".stripMargin
      )
      assert(results.map(_._1).contains(Some("E0016")))
    }

    it("does not report E0016 for a normal, non-cyclic inheritance chain") {
      val results = errors(
        """
          |class A {
          |}
          |class B extends A {
          |}
          |class C extends B {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0;
          |  }
          |}
          |""".stripMargin
      )
      assert(!results.map(_._1).contains(Some("E0016")))
    }
  }
  // #2975: E0016 used to be followed by a StackOverflowError, because the body pass
  // still walked the cyclic supertype chain (a method call looks up extensions on
  // every supertype). The cycle is now cut once E0016 is reported.
  describe("cyclic inheritance used from code (#2975)") {
    def codes(src: String): Seq[Option[String]] = errors(src.stripMargin).map(_._1)

    it("reports E0016, not a crash, when a class conforms to one of two mutually conforming interfaces") {
      val results = codes(
        """
          |interface I conforms J { def a(): Int }
          |interface J conforms I { def b(): Int }
          |class C conforms I {
          |public:
          |  def this { }
          |  def a(): Int = 1
          |  def b(): Int = 2
          |}
          |println(new C().a())
          |""")
      assert(results.contains(Some("E0016")))
      assert(!results.contains(Some("I0000")))
    }

    it("reports E0016, not a crash, when a method is called on a class in a class cycle") {
      val results = codes(
        """
          |class A extends B { public: def this {} def f(): Int = 1 }
          |class B extends A { public: def this {} }
          |println(new A().f())
          |""")
      assert(results.contains(Some("E0016")))
      assert(!results.contains(Some("I0000")))
    }

    it("reports E0016, not a crash, when a method is called through a cyclic interface") {
      val results = codes(
        """
          |interface I conforms J { def a(): Int }
          |interface J conforms I { def b(): Int }
          |def use(i: I): Int = i.a() + i.b()
          |println(1)
          |""")
      assert(results.contains(Some("E0016")))
      assert(!results.contains(Some("I0000")))
    }

    it("reports E0016, not a crash, for a three-class cycle, and still types the rest of the file") {
      val results = codes(
        """
          |class A extends C { public: def this {} }
          |class B extends A { public: def this {} }
          |class C extends B { public: def this {} }
          |val s: String = new B().toString()
          |val n: Int = "not an int"
          |""")
      assert(results.contains(Some("E0016")))
      assert(results.contains(Some("E0000")))
      assert(!results.contains(Some("I0000")))
    }
  }
}
