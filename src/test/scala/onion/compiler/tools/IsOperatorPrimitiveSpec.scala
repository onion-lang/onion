package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #2082: `expr is PrimitiveTypeName` on a reference-typed
 * (Object/boxed) value must compile to an instanceof check against the
 * boxed wrapper class, not crash with NoClassDefFoundError naming the raw
 * JVM primitive descriptor letter (I/J/D/Z/S/B/F) as if it were a class.
 */
class IsOperatorPrimitiveSpec extends AbstractShellSpec {

  describe("the `is` operator against a primitive type name") {

    it("returns true for a matching boxed Long on an Object-typed value") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val v: Object = 1L
          |    return v is Long
          |  }
          |}
          |""".stripMargin,
        "IsLongMatch.on",
        Array()
      )
      assert(Shell.Success(true) == result)
    }

    it("returns false for a non-matching boxed type on an Object-typed value") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val v: Object = "not a long"
          |    return v is Long
          |  }
          |}
          |""".stripMargin,
        "IsLongMismatch.on",
        Array()
      )
      assert(Shell.Success(false) == result)
    }

    it("works for every boxed primitive type name") {
      val result = shell.run(
        """
          |class Main {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val values: Object[] = new Object[7]
          |    values[0] = 1
          |    values[1] = 1L
          |    values[2] = 1.0
          |    values[3] = true
          |    values[4] = 1 as Short
          |    values[5] = 1 as Byte
          |    values[6] = 1.0f
          |    return (values[0] is Int) && (values[1] is Long) && (values[2] is Double) &&
          |      (values[3] is Boolean) && (values[4] is Short) && (values[5] is Byte) &&
          |      (values[6] is Float)
          |  }
          |}
          |""".stripMargin,
        "IsAllPrimitives.on",
        Array()
      )
      assert(Shell.Success(true) == result)
    }
  }
}
