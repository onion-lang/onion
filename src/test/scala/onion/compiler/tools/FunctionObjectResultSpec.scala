package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #260: assigning a lambda to a function type whose result is exactly
 * `Object` must succeed. Return-type inference re-specializes the closure's
 * result (e.g. Object -> Int), but because generic type arguments are invariant
 * the resulting `Function1[String, Int]` must still be usable where the declared
 * `Function1[String, Object]` target is expected.
 */
class FunctionObjectResultSpec extends AbstractShellSpec {
  describe("Function type with an Object result") {
    it("accepts a primitive-returning lambda for a Function1[String, Object] target") {
      val result = shell.run(
        """
          |class ObjResultPrimitive {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Function1[String, Object] = (s: String) -> 42
          |    return f.call("x").toString()
          |  }
          |}
          |""".stripMargin,
        "ObjResultPrimitive.on",
        Array()
      )
      assert(Shell.Success("42") == result)
    }

    it("accepts a String-returning lambda for a Function1[String, Object] target") {
      val result = shell.run(
        """
          |class ObjResultString {
          |public:
          |  static def main(args: String[]): String {
          |    val g: Function1[String, Object] = (s: String) -> s + "!"
          |    return g.call("y").toString()
          |  }
          |}
          |""".stripMargin,
        "ObjResultString.on",
        Array()
      )
      assert(Shell.Success("y!") == result)
    }

    it("still allows widening to a concrete reference supertype (CharSequence)") {
      val result = shell.run(
        """
          |class ResultCharSequence {
          |public:
          |  static def main(args: String[]): String {
          |    val h: Function1[String, CharSequence] = (s: String) -> s + "!"
          |    return h.call("z").toString()
          |  }
          |}
          |""".stripMargin,
        "ResultCharSequence.on",
        Array()
      )
      assert(Shell.Success("z!") == result)
    }

    it("still allows boxing widening to Number") {
      val result = shell.run(
        """
          |class ResultNumber {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Function1[String, Number] = (s: String) -> 7
          |    return n.call("w").toString()
          |  }
          |}
          |""".stripMargin,
        "ResultNumber.on",
        Array()
      )
      assert(Shell.Success("7") == result)
    }

    it("still rejects a genuinely incompatible concrete result type") {
      val result = shell.run(
        """
          |class BadResult {
          |public:
          |  static def main(args: String[]): String {
          |    val bad: Function1[String, Number] = (s: String) -> s + "!"
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "BadResult.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
