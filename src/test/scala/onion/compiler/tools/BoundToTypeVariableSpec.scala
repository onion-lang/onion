package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * A value of a type variable's bound is not a `T`: inside
 * `class J[T extends Comparable[T]]`, a `Comparable[T]` put into a `T` slot is
 * E0000, as in Java. It used to compile and then throw a ClassCastException
 * far from the mistake (#2366). `(c as T)` stays the escape hatch.
 */
class BoundToTypeVariableSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a value of T's bound, put into a T slot") {
    it("is rejected as an expression-body return (the issue's repro)") {
      val src =
        """class J[T extends Comparable[T]] {
          |public:
          |  def back(c: Comparable[T]): T = c
          |}
          |class Fake conforms Comparable[String] {
          |public:
          |  def compareTo(o: String): Int = 0
          |}
          |def main(args: String[]): void {
          |  val s: String = new J[String]().back(new Fake())
          |  IO::println(s.length())
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000"))
    }

    it("is rejected in a return statement, a T? field and a T local") {
      val src =
        """class J[T extends Comparable[T]] {
          |  var t: T? = null
          |public:
          |  def a(c: Comparable[T]): T { return c }
          |  def b(c: Comparable[T]): void { t = c }
          |  def d(c: Comparable[T]): void { val x: T = c }
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq("E0000", "E0000", "E0000"))
    }

    it("is accepted with an explicit cast, which runs when the value really is a T") {
      val r = shell.run(
        """class J[T extends Comparable[T]] {
          |public:
          |  def back(c: Comparable[T]): T = (c as T)
          |}
          |def main(args: String[]): Int {
          |  val s: String = new J[String]().back("abc")
          |  return s.length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
  }
}
