package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

class LocalFunctionShadowsImportSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class T {
         |public:
         |  static def main(args: String[]): String {
         |$body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("a local function value and a default-imported static of the same name") {
    it("calls the local `first`, not Iterables::first (#3075)") {
      val r = run(
        """    val first: (List[Int]) -> Int = (ys) -> 42
          |    return "" + first([1, 2, 3])""".stripMargin)
      assert(Shell.Success("42") == r)
    }

    it("calls the local `last`") {
      val r = run(
        """    val last: (List[Int]) -> Int = (ys) -> 99
          |    return "" + last([1, 2, 3])""".stripMargin)
      assert(Shell.Success("99") == r)
    }

    it("still calls the import when no local of that name exists") {
      val r = run("""    return "" + first([7, 8])""")
      assert(Shell.Success("7") == r)
    }
    it("calls the local when an argument is an untyped lambda") {
      val r = run(
        """    val map: (List[Int], (Int) -> Int) -> Int = (xs, f) -> 42
          |    return "" + map([1, 2], x -> x * 2)""".stripMargin)
      assert(Shell.Success("42") == r)
    }

    it("calls the local when the argument is a trailing lambda") {
      val r = run(
        """    val map: (List[Int], (Int) -> Int) -> Int = (xs, f) -> 42
          |    return "" + map([1, 2]) { x -> x * 2 }""".stripMargin)
      assert(Shell.Success("42") == r)
    }

    it("types the untyped lambda against the local's parameter type") {
      val r = run(
        """    val filter: (List[Int], (Int) -> Boolean) -> Int = (xs, p) -> if p(5) { 1 } else { 0 }
          |    return "" + filter([1, 2], x -> x > 1)""".stripMargin)
      assert(Shell.Success("1") == r)
    }

    it("calls a local with no same-named import when an argument is an untyped lambda") {
      val r = run(
        """    val twice: (Int, (Int) -> Int) -> Int = (n, f) -> f(f(n))
          |    return "" + twice(3, x -> x + 1)""".stripMargin)
      assert(Shell.Success("5") == r)
    }

    it("still calls the import with an untyped lambda when no local of that name exists") {
      val r = run("""    return "" + map([1, 2], x -> x * 2)""")
      assert(Shell.Success("[2, 4]") == r)
    }
    it("reports E0070 for a nullable local called with a lambda, instead of calling the import") {
      val codes = errorCodes(
        """class T {
          |public:
          |  static def main(args: String[]): String {
          |    val map: Function2[List[Int], Function1[Int, Int], Int]? = null
          |    return "" + map([1, 2], x -> x * 2)
          |  }
          |}
          |""".stripMargin)
      assert(codes.contains("E0070"), s"expected E0070 in $codes")
    }
  }
  describe("a local function value and a same-named method of the enclosing class") {
    def runClass(src: String): Shell.Result = shell.run(src, "None", Array())

    it("still calls the static method taking a Java SAM type when the lambda is typed") {
      val r = runClass(
        """import { java.util.function.IntUnaryOperator }
          |class C {
          |public:
          |  static def apply(op: IntUnaryOperator): Int = op.applyAsInt(10)
          |  static def main(args: String[]): String {
          |    val apply: ((Int) -> Int) -> Int = (f) -> 777
          |    return "" + apply((x: Int) -> x + 1) + " " + apply(x -> x + 1) + " " + apply { x -> x + 1 }
          |  }
          |}
          |""".stripMargin)
      assert(Shell.Success("11 11 11") == r)
    }

    it("still calls the instance method taking a Java SAM type when the lambda is typed") {
      val r = runClass(
        """import { java.util.function.IntUnaryOperator }
          |class C {
          |public:
          |  def apply(op: IntUnaryOperator): Int = op.applyAsInt(10)
          |  def go(): String {
          |    val apply: ((Int) -> Int) -> Int = (f) -> 777
          |    return "" + apply((x: Int) -> x + 1) + " " + apply(x -> x + 1)
          |  }
          |  static def main(args: String[]): String = new C().go()
          |}
          |""".stripMargin)
      assert(Shell.Success("11 11") == r)
    }
  }
}
