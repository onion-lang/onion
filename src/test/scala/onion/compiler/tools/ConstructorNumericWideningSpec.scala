package onion.compiler.tools

import onion.tools.Shell

/**
 * A constructor argument that needs ordinary numeric widening (an `Int`
 * literal passed where a `Double`/`Float`/`Long` parameter is declared, e.g.
 * `new Game(99)` against `record Game(price: Double)`) used to crash
 * `BytecodeGeneration` with an internal `I0000` error. `findConstructorWithBoxing`
 * only wrapped the argument in a widening `AsInstanceOf` conversion for the
 * Byte/Short/Char constant-narrowing case; a genuine widening match (accepted
 * as assignable by `TypeRelations.isAssignableWithBoxing`) left the argument
 * term unconverted, so codegen pushed a 1-slot `int` where the constructor's
 * descriptor declared a 2-slot `double`/`long`, corrupting the JVM stack map
 * frames (`NegativeArraySizeException` in ASM's `Frame.merge`). Found by
 * `MutationFuzzSpec` mutating a decimal literal (`59.99` -> `99`) in
 * `run/GameStore.on`.
 */
class ConstructorNumericWideningSpec extends AbstractShellSpec {
  describe("numeric widening at a constructor call site") {
    it("widens an Int literal to a Double record component") {
      assert(Shell.Success(9900) == shell.run(
        """
          |record Game(price: Double)
          |def main(args: String[]): Int { return (new Game(99).price() * 100.0) as Int }
        """.stripMargin, "None", Array()))
    }
    it("widens an Int literal to a Float record component") {
      assert(Shell.Success(9900) == shell.run(
        """
          |record Game(price: Float)
          |def main(args: String[]): Int { return (new Game(99).price() * 100.0f) as Int }
        """.stripMargin, "None", Array()))
    }
    it("widens an Int literal to a Long record component") {
      assert(Shell.Success(99) == shell.run(
        """
          |record Game(stock: Long)
          |def main(args: String[]): Int { return new Game(99).stock() as Int }
        """.stripMargin, "None", Array()))
    }
    it("widens an Int literal to a Double parameter of a plain class constructor") {
      assert(Shell.Success(9900) == shell.run(
        """
          |class C {
          |  public: val v: Double
          |  def this(v: Double) { this.v = v }
          |}
          |def main(args: String[]): Int { return (new C(99).v * 100.0) as Int }
        """.stripMargin, "None", Array()))
    }
    it("still widens alongside an unrelated Byte/Short narrowing argument") {
      assert(Shell.Success(9903) == shell.run(
        """
          |record Mixed(price: Double, tag: Byte)
          |def main(args: String[]): Int { return (new Mixed(99, 3).price() * 100.0) as Int + new Mixed(99, 3).tag() }
        """.stripMargin, "None", Array()))
    }
  }
}
