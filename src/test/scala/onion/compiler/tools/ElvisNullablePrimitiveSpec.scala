package onion.compiler.tools

import onion.tools.Shell

/**
 * The elvis operator `a ?: b` must work for nullable primitives (Int?/Long?/
 * Double?/Boolean?), not only nullable references. Previously a primitive
 * fallback (e.g. `n ?: -1`) failed with E0001 because the operator rejected any
 * primitive operand and had no boxing bridge.
 */
class ElvisNullablePrimitiveSpec extends AbstractShellSpec {
  private def run(body: String, ret: String = "Int"): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): $ret {
         |$body
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("elvis with nullable primitives") {
    it("Int? ?: primitive falls back when null") {
      assert(Shell.Success(-1) == run("    val n: Int? = null\n    return n ?: -1"))
    }
    it("Int? ?: primitive keeps the value when non-null") {
      assert(Shell.Success(42) == run("    val n: Int? = 42\n    return n ?: -1"))
    }
    it("safe-call chain s?.length() ?: -1 (the canonical idiom)") {
      assert(Shell.Success(-1) == run("    val s: String? = null\n    return s?.length() ?: -1"))
      assert(Shell.Success(5) == run("    val s: String? = \"hello\"\n    return s?.length() ?: -1"))
    }
    it("Long? and Double? fallbacks") {
      assert(Shell.Success(7L) == run("    val x: Long? = null\n    return x ?: 7L", "Long"))
      assert(Shell.Success(3.5) == run("    val d: Double? = null\n    return d ?: 3.5", "Double"))
    }
    it("still works for nullable references") {
      assert(Shell.Success("default") == run("    val s: String? = null\n    return s ?: \"default\"", "String"))
    }
  }
}
