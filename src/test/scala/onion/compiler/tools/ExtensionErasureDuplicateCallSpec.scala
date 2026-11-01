package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

class ExtensionErasureDuplicateCallSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _ => Seq.empty
    }
  }

  private val lambdaTwins =
    """extension String {
      |  def p(f: (Int) -> Int): String = "i"
      |  def p(f: (String) -> String): String = "s"
      |}
      |""".stripMargin

  private val swappedLambdaTwins =
    """extension String {
      |  def p(f: (String) -> String): String = "s"
      |  def p(f: (Int) -> Int): String = "i"
      |}
      |""".stripMargin

  private val listTwins =
    """extension String {
      |  def q(xs: List[Int]): String = "i"
      |  def q(xs: List[String]): String = "s"
      |}
      |""".stripMargin

  describe("a call to an extension name that has an erasure duplicate in its block (#2475)") {
    it("reports only E0084 when the call matches the first twin") {
      assert(codes(lambdaTwins + "IO::println(\"a\".p((x: Int) -> x))\n") == Seq(Some("E0084")))
    }

    it("reports only E0084 when the call matches the second twin") {
      assert(codes(lambdaTwins + "IO::println(\"a\".p((x: String) -> x))\n") == Seq(Some("E0084")))
    }

    it("reports only E0084 whichever order the twins are declared in") {
      assert(codes(swappedLambdaTwins + "IO::println(\"a\".p((x: Int) -> x))\n") == Seq(Some("E0084")))
      assert(codes(swappedLambdaTwins + "IO::println(\"a\".p((x: String) -> x))\n") == Seq(Some("E0084")))
    }

    it("reports only E0084 for non-lambda twins, called with either argument type") {
      assert(codes(listTwins + "val ys: List[String] = [\"a\"]\nIO::println(\"a\".q(ys))\n") == Seq(Some("E0084")))
      assert(codes(listTwins + "val ns: List[Int] = [1]\nIO::println(\"a\".q(ns))\n") == Seq(Some("E0084")))
    }

    it("reports only E0084 for a safe call") {
      assert(codes(listTwins + "val s: String? = \"a\"\nval ys: List[String] = [\"a\"]\nIO::println(s?.q(ys))\n") == Seq(Some("E0084")))
    }

    it("still reports an error inside either twin's body") {
      val src =
        """extension String {
          |  def q(xs: List[Int]): String = "i"
          |  def q(xs: List[String]): String = undefinedName
          |}
          |""".stripMargin
      assert(codes(src).contains(Some("E0084")))
      assert(codes(src).size == 2)
    }

    it("still reports an error inside the call's own arguments") {
      val result = codes(listTwins + "IO::println(\"a\".q(undefinedName))\n")
      assert(result.contains(Some("E0084")))
      assert(result.size == 2)
    }
  }

  describe("extension overloads that differ after erasure") {
    it("still resolve") {
      val result = codes(
        """extension String {
          |  def p(f: (Int) -> Int): String = "i"
          |  def p(n: Int): String = "n"
          |}
          |IO::println("a".p((x: Int) -> x))
          |IO::println("a".p(1))
          |""".stripMargin
      )
      assert(result.isEmpty)
    }
  }
}
