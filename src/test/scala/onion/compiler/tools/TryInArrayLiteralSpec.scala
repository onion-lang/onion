package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as an element of an array-literal
 * (`new T[]{ ... }`) crashed the compiler with an `[I0000]` internal error
 * during bytecode verification, the array-literal sibling of issue #745's
 * field/array-assignment bug: `visitNewArrayWithValues` `dup()`s the array
 * reference before evaluating each element, and the JVM clears the operand
 * stack when it dispatches to an exception handler, so that dup()'d
 * reference was silently discarded on the exceptional path, desyncing the
 * merged stack shape from the normal path.
 *
 * `AsmCodeGenerationVisitor.visitNewArrayWithValues` now spills the array
 * reference into a local before evaluating any element when at least one
 * element may run its own `try`, and reloads it for each `arrayStore`.
 */
class TryInArrayLiteralSpec extends AbstractShellSpec {
  describe("try/catch expression as an array-literal element") {
    it("does not corrupt the array reference across a caught and an uncaught element (issue #747-sibling)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[]{1, try { Integer::parseInt("7") } catch _: Exception { -1 }, 3}
          |    return arr[0] + "|" + arr[1] + "|" + arr[2]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayLiteralElement.on",
        Array()
      )
      assert(Shell.Success("1|7|3") == result)
    }

    it("propagates the caught branch's value when parsing fails") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val arr: Int[] = new Int[]{try { Integer::parseInt("nope") } catch _: Exception { -1 }, 2}
          |    return arr[0] + "|" + arr[1]
          |  }
          |}
          |""".stripMargin,
        "TryInArrayLiteralElementCaught.on",
        Array()
      )
      assert(Shell.Success("-1|2") == result)
    }
  }
}
