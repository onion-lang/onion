package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * Two generic overloads that share a type parameter name and differ only in a later
 * parameter (`tw[T](x: T, y: Int)` / `tw[T](x: T, y: String)`) were treated as the
 * same method: an extension block reported E0084, and on a class, a static or a
 * top-level function one overload vanished from the call's candidates (E0005) (#2445).
 */
class GenericOverloadLaterParameterSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def runMain(decls: String, body: String): Shell.Result =
    shell.run(
      s"""
         |$decls
         |class Main {
         |public:
         |  static def main(args: String[]): String {
         |    return $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("generic overloads that differ in a later parameter") {
    it("are distinct in an extension block") {
      val result = runMain(
        """extension String {
          |  def tw[T](x: T, y: Int): String = "int:" + y
          |  def tw[T](x: T, y: String): String = "str:" + y
          |}""".stripMargin,
        """"a".tw("p", 1) + "," + "a".tw("p", "q")"""
      )
      assert(Shell.Success("int:1,str:q") == result)
    }

    it("are distinct as instance methods") {
      val result = runMain(
        """class C {
          |public:
          |  def tw[T](x: T, y: Int): String = "int:" + y
          |  def tw[T](x: T, y: String): String = "str:" + y
          |}""".stripMargin,
        """new C().tw("p", 1) + "," + new C().tw("p", "q")"""
      )
      assert(Shell.Success("int:1,str:q") == result)
    }

    it("are distinct as static methods") {
      val result = runMain(
        """class C {
          |public:
          |  static def tw[T](x: T, y: Int): String = "int:" + y
          |  static def tw[T](x: T, y: String): String = "str:" + y
          |}""".stripMargin,
        """C::tw("p", 1) + "," + C::tw("p", "q")"""
      )
      assert(Shell.Success("int:1,str:q") == result)
    }

    it("are distinct when a function type follows the type parameter") {
      val result = runMain(
        // The function overload is declared last: a lambda literal is typed against the
        // last-declared extension overload, a separate bug (#2447).
        """extension String {
          |  def g2[T](xs: List[T], s: String): String = "str:" + s
          |  def g2[T](xs: List[T], f: (T) -> String): String = "fn:" + f(xs[0])
          |}""".stripMargin,
        """"a".g2([1], (n: Int) -> "n" + n) + "," + "a".g2([1], "s")"""
      )
      assert(Shell.Success("fn:n1,str:s") == result)
    }

    it("still reports a real duplicate in an extension block as E0084") {
      val codes = errorCodes(
        """extension String {
          |  def tw[T](x: T, y: Int): String = "a"
          |  def tw[T](x: T, y: Int): String = "b"
          |}
          |""".stripMargin
      )
      assert(codes.contains(Some("E0084")))
    }

    it("still reports a real duplicate in a class") {
      val codes = errorCodes(
        """class C {
          |public:
          |  def tw[T](x: T, y: Int): String = "a"
          |  def tw[T](x: T, y: Int): String = "b"
          |}
          |""".stripMargin
      )
      assert(codes.nonEmpty)
    }

    // `(x: T, y: T)` and `(x: T, y: Object)` are distinct overloads but erase to the same
    // JVM method; without a check, the class failed to load with ClassFormatError.
    it("reports top-level functions with the same erasure as E0035") {
      val codes = errorCodes(
        """def f[T](x: T, y: T): String = "tt"
          |def f[T](x: T, y: Object): String = "to"
          |IO::println("hi")
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0035")))
    }

    it("reports extension methods with the same erasure as E0035") {
      val codes = errorCodes(
        """extension String {
          |  def f[T](x: T, y: T): String = "tt"
          |  def f[T](x: T, y: Object): String = "to"
          |}
          |IO::println("hi")
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0035")))
    }

    it("reports a bounded type parameter erasing to a concrete one as E0035") {
      val codes = errorCodes(
        """def g[T extends String](x: T, y: T): String = "a"
          |def g[T extends String](x: T, y: String): String = "b"
          |IO::println("hi")
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0035")))
    }

    it("reports a real duplicate top-level function once, as E0012") {
      val codes = errorCodes(
        """def f[T](x: T, y: Int): String = "a"
          |def f[T](x: T, y: Int): String = "b"
          |IO::println("hi")
          |""".stripMargin
      )
      assert(codes == Seq(Some("E0012")))
    }
  }
}
