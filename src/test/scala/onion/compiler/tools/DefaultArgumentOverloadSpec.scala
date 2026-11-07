package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

/**
 * A default belongs to the method a call resolves to at compile time (#2548). Since #2398 a
 * default became a virtual method named after the method and the position, so a subclass
 * method with the same name and leading parameter types silently replaced the inherited
 * default (`sh.scale(5)` gave 500, not 5), or clashed with it in E0104 under a `$default$`
 * name the user never wrote.
 */
class DefaultArgumentOverloadSpec extends AbstractShellSpec {
  private def run(src: String): Shell.Result = shell.run(src.stripMargin, "None", Array())

  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src.stripMargin), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
      case _ => Seq.empty
    }
  }

  describe("a subclass method with the same name (#2548)") {
    it("does not take over the inherited method's default (repro 1)") {
      val result = run(
        """
          |class Shape {
          |public:
          |  def this { }
          |  def scale(x: Int, factor: Int = 1): Int = x * factor
          |}
          |class Square extends Shape {
          |public:
          |  def this { }
          |  def scale(x: Int, factor: Int = 100, label: String): String = label + (x * factor)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sq = new Square()
          |    val sh: Shape = sq
          |    return sq.scale(5) + "," + sh.scale(5) + "," + sq.scale(5, label = "L")
          |  }
          |}
        """)
      assert(Shell.Success("5,5,L500") == result)
    }

    it("compiles an overload whose default has another type (repro 2), with no $default$ in any message") {
      val src =
        """
          |class A {
          |public:
          |  def this {}
          |  def f(a: Int, b: Int = 1): String = "A" + (a + b)
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  def f(a: Int, b: String = "x"): String = "B" + a + b
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = new B().f(1, 2) + "," + new B().f(1, "y")
          |}
        """
      val errs = errors(src)
      assert(errs.isEmpty, errs.map(_.message).mkString("\n"))
      assert(!errs.exists(_.message.contains("$default$")))
      assert(Shell.Success("A3,B1y") == run(src))
    }

    it("an override's default is used through the subclass, the parent's through the parent") {
      val result = run(
        """
          |class A {
          |public:
          |  def this {}
          |  def f(n: Int = 5): Int = n
          |}
          |class B extends A {
          |public:
          |  def this {}
          |  override def f(n: Int = 9): Int = n * 100
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new B()
          |    val a: A = b
          |    return a.f() + "," + b.f()
          |  }
          |}
        """)
      assert(Shell.Success("500,900") == result)
    }

    it("keeps each default apart under a generic parent") {
      val result = run(
        """
          |class A[T] {
          |public:
          |  def this {}
          |  def f(x: T, n: Int = 1): String = "A" + x + n
          |}
          |class B extends A[String] {
          |public:
          |  def this {}
          |  override def f(x: String, n: Int = 2): String = "B" + x + n
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new B()
          |    val a: A[String] = b
          |    return a.f("s") + "," + b.f("s")
          |  }
          |}
        """)
      assert(Shell.Success("Bs1,Bs2") == result)
    }
  }

  describe("E0077 names a default by its method (#2548 panel block)") {
    it("says `calling stamp`, never the synthesized `stamp$default$1...`") {
      val errs = errors(
        """
          |def stamp(n: Long = System::currentTimeMillis()): Long = n
          |tool now(): Long { return stamp() }
        """)
      val e0077 = errs.filter(_.errorCode.contains("E0077"))
      assert(e0077.nonEmpty, errs.map(_.message).mkString("\n"))
      assert(!errs.exists(_.message.contains("$default$")), errs.map(_.message).mkString("\n"))
      assert(e0077.exists(_.message.contains("stamp")), errs.map(_.message).mkString("\n"))
    }

    it("names a class method's default as `Owner::method`") {
      val errs = errors(
        """
          |class Clock {
          |public:
          |  def this {}
          |  def stamp(n: Long = System::currentTimeMillis()): Long = n
          |}
          |tool now(): Long { return new Clock().stamp() }
        """)
      val e0077 = errs.filter(_.errorCode.contains("E0077"))
      assert(e0077.nonEmpty, errs.map(_.message).mkString("\n"))
      assert(!errs.exists(_.message.contains("$default$")), errs.map(_.message).mkString("\n"))
      assert(e0077.exists(_.message.contains("Clock::stamp")), errs.map(_.message).mkString("\n"))
    }
  }
}
