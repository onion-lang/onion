package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

/** `x?.m { y -> .. }` takes the lambda's parameter types from `m`, as `x.m { y -> .. }` does (#3007). */
class SafeCallUntypedLambdaSpec extends AbstractShellSpec {
  private val box =
    """
      |class Box {
      |public:
      |  def this {}
      |  def lam(f: (Int) -> Int): Int = f(5)
      |  def twice(n: Int, f: (Int) -> Int): Int = f(f(n))
      |}
      |""".stripMargin

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def program(body: String): String =
      box +
        s"""
           |class Main {
           |public:
           |  static def main(args: String[]): String {
           |$body
           |  }
           |}
           |""".stripMargin

  private def run(body: String): Shell.Result = shell.run(program(body), "None", Array())

  describe("A safe call with an untyped lambda argument (#3007)") {
    it("infers the lambda's parameter from a stdlib extension: xs?.map { x -> x * 2 }") {
      assert(run(
        """    val xs: List[Int]? = [1, 2, 3]
          |    return "" + xs?.map { x -> x * 2 }""".stripMargin) == Shell.Success("[2, 4, 6]"))
    }

    it("short-circuits to null on a null receiver, without running the lambda") {
      assert(run(
        """    val xs: List[Int]? = null
          |    return "" + xs?.map { x -> x * 2 }""".stripMargin) == Shell.Success("null"))
    }

    it("infers the parameter from a user method, as a trailing lambda and as an argument") {
      assert(run(
        """    val a: Box? = new Box()
          |    return "" + a?.lam { x -> x + 1 } + "," + a?.lam((x) -> x * 3) + "," + a?.twice(1) { x -> x * 10 }""".stripMargin)
        == Shell.Success("6,15,100"))
    }

    it("is null for a null receiver of a user method") {
      assert(run(
        """    val a: Box? = null
          |    return "" + a?.lam { x -> x + 1 }""".stripMargin) == Shell.Success("null"))
    }

    it("has a nullable result type, so it cannot initialise a non-null Int") {
      val codes = errorCodes(program(
        """    val a: Box? = new Box()
          |    val n: Int = a?.lam { x -> x + 1 }
          |    return "" + n""".stripMargin))
      assert(codes.nonEmpty)
      assert(!codes.contains("E0052"))
    }

    it("works with ?: as the fallback") {
      assert(run(
        """    val a: Box? = null
          |    val n: Int = a?.lam { x -> x + 1 } ?: -1
          |    return "" + n""".stripMargin) == Shell.Success("-1"))
    }

    it("evaluates the receiver once") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  def this {}
          |  def lam(f: (Int) -> Int): Int = f(5)
          |}
          |
          |class Main {
          |  static var made: Int = 0
          |public:
          |  static def make(): Box? {
          |    made = made + 1
          |    return new Box()
          |  }
          |  static def main(args: String[]): String {
          |    val r = make()?.lam { x -> x + 1 }
          |    return "" + r + " made=" + made
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result == Shell.Success("6 made=1"))
    }

    it("still reports E0013 for a private method another class calls through ?.") {
      val codes = errorCodes(
        """
          |class Box {
          |  def hidden(f: (Int) -> Int): Int = f(5)
          |public:
          |  def this {}
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val a: Box? = new Box()
          |    return "" + a?.hidden { x -> x + 1 }
          |  }
          |}
          |""".stripMargin
      )
      assert(codes == Seq("E0013"))
    }
  }
}
