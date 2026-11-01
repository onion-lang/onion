package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Two constructors whose parameters erase to the same JVM types (`this(x: T)` beside
 * `this(x: Object)` in a class `O[T]`) compiled clean, then the class failed to load
 * with `ClassFormatError: Duplicate method name "<init>"` (#2498). They are E0035, as two
 * methods with the same erasure are.
 */
class ConstructorErasureCollisionSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("constructors with the same erasure") {
    it("are E0035 for this(x: T) beside this(x: Object)") {
      val codes = errorCodes(
        """class O[T] {
          |public:
          |  def this(x: T) { }
          |  def this(x: Object) { }
          |}
          |val o = new O[String]("a")
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0035")))
    }

    it("are E0035 for a bounded type parameter beside its bound") {
      val codes = errorCodes(
        """class B[T extends CharSequence] {
          |public:
          |  def this(x: T, n: Int) { }
          |  def this(x: CharSequence, n: Int) { }
          |}
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0035")))
    }

    it("are one error for List[String] beside List[Int]") {
      val codes = errorCodes(
        """class L {
          |public:
          |  def this(xs: List[String]) { }
          |  def this(xs: List[Int]) { }
          |}
          |""".stripMargin
      )
      // Already caught as a duplicate constructor (E0025); it must stay exactly one error.
      assert(codes.size == 1)
    }

    it("stay a plain duplicate (one error) when the declared types are identical") {
      val codes = errorCodes(
        """class D {
          |public:
          |  def this(x: Int) { }
          |  def this(x: Int) { }
          |}
          |""".stripMargin
      )
      assert(codes.size == 1)
      assert(!codes.contains(Some("E0035")))
    }
  }

  describe("constructors with different erasures") {
    it("still compile and run") {
      val result = shell.run(
        """class O[T] {
          |public:
          |  var tag: String = ""
          |  def this(x: T, n: Int) { tag = "t" }
          |  def this(x: Object) { tag = "o" }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return new O[String]("a", 1).tag + new O[String]("b").tag
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("to") == result)
    }
  }
}
