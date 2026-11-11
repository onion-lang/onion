package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #2667: a static-imported function did not give an untyped lambda argument its
 * parameter types. `Maps::filterValues(m) { v -> v > 1 }` compiled, while the same call
 * after `import { onion.Maps::* }`, written `filterValues(m) { v -> v > 1 }`, was E0052.
 */
class StaticImportLambdaSpec extends AbstractShellSpec {
  private def run(source: String): Shell.Result = shell.run(source, "None", Array())

  describe("An untyped lambda passed to a static-imported function") {
    it("takes its parameter types from a class-level import, as a trailing lambda") {
      val result = run(
        """import { onion.Maps::* }
          |class T {
          |public:
          |  static def main(args: String[]): Object {
          |    val m = ["a": 1, "b": 2]
          |    return filterValues(m) { v -> v > 1 }.toString()
          |  }
          |}
          |""".stripMargin)
      assert(result == Shell.Success("{b=2}"))
    }

    it("takes its parameter types as a positional argument") {
      val result = run(
        """import { onion.Maps::* }
          |class T {
          |public:
          |  static def main(args: String[]): Object {
          |    val m = ["a": 1, "b": 2]
          |    return filterValues(m, (v) -> v > 1).toString()
          |  }
          |}
          |""".stripMargin)
      assert(result == Shell.Success("{b=2}"))
    }

    it("takes its parameter types from a single-method import") {
      val result = run(
        """import { onion.Maps::filterValues }
          |class T {
          |public:
          |  static def main(args: String[]): Object {
          |    val m = ["a": 1, "b": 2]
          |    return filterValues(m) { v -> v > 1 }.toString()
          |  }
          |}
          |""".stripMargin)
      assert(result == Shell.Success("{b=2}"))
    }

    it("picks the imported class whose overload fits the other arguments") {
      val result = run(
        """import {
          |  onion.Maps::*
          |  onion.Sets::*
          |}
          |class T {
          |public:
          |  static def main(args: String[]): Object {
          |    val m = ["a": 1, "b": 2]
          |    return filter(m) { k, v -> k == "a" }.toString()
          |  }
          |}
          |""".stripMargin)
      assert(result == Shell.Success("{a=1}"))
    }

    it("works for Colls::sumBy") {
      val result = run(
        """import { onion.Colls::* }
          |class T {
          |public:
          |  static def main(args: String[]): Object {
          |    val xs = [1, 2, 3]
          |    return sumBy(xs) { x -> x * 2 }
          |  }
          |}
          |""".stripMargin)
      assert(result == Shell.Success(12.0))
    }

    it("still fails to compile when no imported function has that name") {
      val result = run(
        """import { onion.Maps::* }
          |class T {
          |public:
          |  static def main(args: String[]): Object {
          |    val m = ["a": 1]
          |    return noSuchFunction(m) { v -> v }
          |  }
          |}
          |""".stripMargin)
      assert(result == Shell.Failure(-1))
    }
  }
}
