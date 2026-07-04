package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #280: a local `val` declared without an initializer used to be silently
 * accepted; a later read yielded the JVM default (null/0) or a raw NPE with no
 * diagnostic. It must now be a compile error (E0069 VAL_REQUIRES_INITIALIZER),
 * while a `val` that IS initialized still compiles and runs normally.
 */
class UninitializedValSpec extends AbstractShellSpec {
  describe("uninitialized local val (issue #280)") {
    it("rejects an uninitialized local val that is later read") {
      val result = shell.run(
        """
          |class UninitVal {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: String
          |    IO::println(x)
          |    return 0
          |  }
          |}
        """.stripMargin,
        "UninitVal.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects an uninitialized local val of primitive type") {
      val result = shell.run(
        """
          |class UninitValInt {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int
          |    return x
          |  }
          |}
        """.stripMargin,
        "UninitValInt.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects an uninitialized local val inside a nested block") {
      val result = shell.run(
        """
          |class UninitValNested {
          |public:
          |  static def main(args: String[]): Int {
          |    if true {
          |      val y: Int
          |      IO::println(y)
          |    }
          |    return 0
          |  }
          |}
        """.stripMargin,
        "UninitValNested.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("accepts a local val that is initialized at its declaration") {
      val result = shell.run(
        """
          |class InitVal {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 42
          |    return x
          |  }
          |}
        """.stripMargin,
        "InitVal.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }

    it("still accepts a var declared without an initializer and assigned later") {
      val result = shell.run(
        """
          |class VarNoInit {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int
          |    x = 7
          |    return x
          |  }
          |}
        """.stripMargin,
        "VarNoInit.on",
        Array()
      )
      assert(Shell.Success(7) == result)
    }

    it("still accepts a field val initialized in the constructor") {
      val result = shell.run(
        """
          |class Box {
          |  val v: Int
          |public:
          |  def this { v = 9 }
          |  def get(): Int = v
          |  static def main(args: String[]): Int {
          |    return new Box().get()
          |  }
          |}
        """.stripMargin,
        "BoxField.on",
        Array()
      )
      assert(Shell.Success(9) == result)
    }
  }
}
