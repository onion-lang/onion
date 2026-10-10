package onion.compiler.tools

/**
 * Regression for #2034: a lambda with explicitly typed parameters, passed to an
 * unqualified call (a top-level function) whose parameter is a Java functional
 * interface, used to be typed as FunctionN and rejected with E0005.
 */
class TypedLambdaUnqualifiedSamSpec extends AbstractShellSpec {
  private def runReadingProperty(key: String, script: String): String = {
    System.clearProperty(key)
    shell.run(script, "None", Array())
    System.getProperty(key)
  }

  describe("typed lambda passed to a top-level function taking a Java SAM") {
    it("converts a one-parameter lambda (#2034)") {
      val v = runReadingProperty(
        "test2034.unary",
        """
          |import { java.util.function.IntUnaryOperator }
          |def useOp(f: IntUnaryOperator): Int = f.applyAsInt(5)
          |System::setProperty("test2034.unary", "" + useOp((x: Int) -> x * 2))
          |""".stripMargin
      )
      assert(v == "10")
    }

    it("converts a two-parameter lambda to a Comparator (#2034)") {
      val v = runReadingProperty(
        "test2034.cmp",
        """
          |import { java.util.Comparator }
          |def useCmp(f: Comparator[Int]): Int = f.compare(1, 2)
          |System::setProperty("test2034.cmp", "" + useCmp((a: Int, b: Int) -> b - a))
          |""".stripMargin
      )
      assert(v == "1")
    }
  }
}
