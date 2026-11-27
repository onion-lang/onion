package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import java.io.StringReader

/** A method whose parameter is the primitive where a non-generic supertype's abstract method
  * has the boxed class (`n(x: Int)` for `n(x: JInteger)`), or the reverse, is an overload: no
  * bridge joins the two (#2896's row 5). A concrete class with no other implementation is
  * E0037 at compile time, not `AbstractMethodError` at the call (#2922). A generic supertype
  * (`G[JInteger]` with `n(x: T)`) and a parameter that differs only by `?` are still bridged. */
class BoxedAbstractImplementationSpec extends AbstractShellSpec {
  private def outcome(src: String): (Seq[String], String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (o, e) = (System.out, System.err)
    var messages = ""
    val codes =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) {
          val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
          new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
            case CompilationOutcome.Failure(errs) =>
              messages = errs.map(_.message).mkString("\n")
              errs.flatMap(_.errorCode)
            case _ =>
              shell.run(src, "None", Array())
              Seq.empty
          }
        } }
      } finally { System.setOut(o); System.setErr(e) }
    (codes, messages + new String(buf.toByteArray, "UTF-8"))
  }

  // The hint names the method as written and the spelling that implements the contract.
  private def rejects(src: String, written: String, wanted: String): Unit = {
    val (codes, out) = outcome(src)
    assert(codes.contains("E0037"), s"expected E0037, got $codes\n$out")
    assert(out.contains(written) && out.contains(wanted), s"expected '$written' and '$wanted' in the hint, got:\n$out")
  }

  private def prints(src: String, expected: String): Unit = {
    val (codes, out) = outcome(src)
    assert(codes.isEmpty, s"expected no compile error, got $codes\n$out")
    assert(out.contains(expected), s"expected '$expected' in output, got:\n$out")
  }

  describe("a primitive/boxed parameter against a non-generic abstract method") {
    it("is E0037 for n(Int) beside an interface's n(JInteger) (#2922)") {
      rejects(
        """interface I { def n(x: JInteger): String }
          |class C conforms I {
          |public:
          |  def this {}
          |  def n(x: Int): String = "int " + x
          |}
          |val i: I = new C
          |println(i.n(4))
          |""".stripMargin, "n(Int)", "n(JInteger)")
    }

    it("is E0037 for n(JInteger) beside an interface's n(Int)") {
      rejects(
        """interface I { def n(x: Int): String }
          |class C conforms I {
          |public:
          |  def this {}
          |  def n(x: JInteger): String = "boxed " + x
          |}
          |val i: I = new C
          |println(i.n(4))
          |""".stripMargin, "n(JInteger)", "n(Int)")
    }

    it("is E0037 for n(Int) beside an abstract class's n(JInteger)") {
      rejects(
        """abstract class A {
          |public:
          |  def this {}
          |  abstract def n(x: JInteger): String
          |}
          |class C extends A {
          |public:
          |  def this {}
          |  def n(x: Int): String = "int " + x
          |}
          |val a: A = new C
          |println(a.n(4))
          |""".stripMargin, "n(Int)", "n(JInteger)")
    }

    it("is E0037 for accept(JInteger) beside a Java interface's accept(int)") {
      rejects(
        """import { java.util.function.IntConsumer }
          |class C conforms IntConsumer {
          |public:
          |  def this {}
          |  def accept(x: JInteger): void { println("boxed " + x) }
          |}
          |val c: IntConsumer = new C
          |c.accept(4)
          |""".stripMargin, "accept(JInteger)", "accept(Int)")
    }
  }

  describe("what still compiles and runs") {
    it("bridges n(Int) for a generic G[JInteger]'s n(T)") {
      prints(
        """interface G[T] { def n(x: T): String }
          |class C conforms G[JInteger] {
          |public:
          |  def this {}
          |  def n(x: Int): String = "int " + x
          |}
          |val g: G[JInteger] = new C
          |println(g.n(4))
          |""".stripMargin, "int 4")
    }

    it("bridges n(Int?) for an interface's n(Int)") {
      prints(
        """interface I { def n(x: Int): String }
          |class C conforms I {
          |public:
          |  def this {}
          |  def n(x: Int?): String = "nullable " + x
          |}
          |val i: I = new C
          |println(i.n(4))
          |""".stripMargin, "nullable 4")
    }

    it("accepts n(Int) when an exact n(JInteger) implements the interface") {
      prints(
        """interface I { def n(x: JInteger): String }
          |class C conforms I {
          |public:
          |  def this {}
          |  def n(x: Int): String = "int " + x
          |  def n(x: JInteger): String = "boxed " + x
          |}
          |val i: I = new C
          |println(i.n(4))
          |""".stripMargin, "boxed 4")
    }
  }
}
