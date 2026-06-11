package onion.compiler.tools

import onion.tools.Shell

/**
 * Smart casts on parameters: a parameter that is never assigned in the
 * body behaves like a val, so `is` and null checks narrow it. Assigned
 * parameters stay mutable and are not narrowed.
 */
class ParameterSmartCastSpec extends AbstractShellSpec {

  describe("Parameter smart casts") {
    it("narrows a method parameter with is") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def describe(o: Object): String {
          |    if o is String {
          |      return "len=" + o.length()
          |    }
          |    return "other"
          |  }
          |  static def main(args: String[]): String {
          |    return Test::describe("abcd") + "," + Test::describe(new Integer(1))
          |  }
          |}
          |""".stripMargin,
        "ParamIsNarrow.on",
        Array()
      )
      assert(Shell.Success("len=4,other") == result)
    }

    it("narrows a nullable method parameter with a null check") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def measure(s: String?): String {
          |    if s != null { return "n=" + s.length() }
          |    return "null"
          |  }
          |  static def main(args: String[]): String {
          |    return Test::measure("xyz") + "," + Test::measure(null)
          |  }
          |}
          |""".stripMargin,
        "ParamNullNarrow.on",
        Array()
      )
      assert(Shell.Success("n=3,null") == result)
    }

    it("narrows lambda parameters") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f = (s: String?) -> if s != null { "L" + s.length() } else { "nil" }
          |    return f.call("ab") + "," + f.call(null)
          |  }
          |}
          |""".stripMargin,
        "LambdaParamNarrow.on",
        Array()
      )
      assert(Shell.Success("L2,nil") == result)
    }

    it("does not narrow a parameter that is assigned in the body") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(o: Object): String {
          |    if o is String {
          |      o = "changed"
          |      return "" + o.length()
          |    }
          |    return "x"
          |  }
          |  static def main(args: String[]): String { return "no" }
          |}
          |""".stripMargin,
        "ParamAssignedNoNarrow.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("narrows the else branch of a negated is-check") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(o: Object): String {
          |    if !(o is String) {
          |      return "not-str"
          |    } else {
          |      return "len" + o.length()
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return Test::f("abc") + "/" + Test::f(new Integer(1))
          |  }
          |}
          |""".stripMargin,
        "NegatedIsNarrow.on",
        Array()
      )
      assert(Shell.Success("len3/not-str") == result)
    }

    it("narrows the else branch of a negated null check") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def f(s: String?): String {
          |    if !(s != null) {
          |      return "nil"
          |    } else {
          |      return "n" + s.length()
          |    }
          |  }
          |  static def main(args: String[]): String {
          |    return Test::f("ab") + "/" + Test::f(null)
          |  }
          |}
          |""".stripMargin,
        "NegatedNullNarrow.on",
        Array()
      )
      assert(Shell.Success("n2/nil") == result)
    }

    it("still allows assigning to parameters") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def countdown(n: Int): String {
          |    var r = ""
          |    while n > 0 {
          |      r = r + n
          |      n = n - 1
          |    }
          |    return r
          |  }
          |  static def main(args: String[]): String {
          |    return Test::countdown(3)
          |  }
          |}
          |""".stripMargin,
        "ParamStillAssignable.on",
        Array()
      )
      assert(Shell.Success("321") == result)
    }
  }
}
