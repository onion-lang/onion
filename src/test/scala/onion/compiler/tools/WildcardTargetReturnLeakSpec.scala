package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for issue #1972: a lambda whose declared type carries an
 * unrelated wildcard type argument (`Function1[Int, ?]`) corrupted generic
 * type-parameter inference for a nested generic call in its body. The
 * wildcard's collapsed bound (`Object`) was unified against the already-
 * inferred, concrete type parameter from the nested call's own argument
 * (e.g. `Int`), producing a bogus E0000 ("type Int is expected, but type
 * Object is used") that named neither `?` nor `Object` in a way a reader
 * could connect back to the actual cause.
 */
class WildcardTargetReturnLeakSpec extends AbstractShellSpec {

  describe("a wildcarded target type's irrelevant return slot") {
    it("does not corrupt a nested generic call's own type-parameter inference (Int)") {
      val result = shell.run(
        """
          |def withT[T](body: Function0[T]): T = body.call()
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Function1[Int, ?] = (v) -> withT { -> v * 2 }
          |    return "reached"
          |  }
          |}
          |""".stripMargin,
        "WildcardTargetReturnLeakInt.on",
        Array()
      )
      assert(Shell.Success("reached") == result)
    }

    it("does not corrupt a nested generic call's own type-parameter inference (Boolean)") {
      val result = shell.run(
        """
          |def withT[T](body: Function0[T]): T = body.call()
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Function1[Int, ?] = (v) -> withT { -> v > 0 }
          |    return "reached"
          |  }
          |}
          |""".stripMargin,
        "WildcardTargetReturnLeakBoolean.on",
        Array()
      )
      assert(Shell.Success("reached") == result)
    }
  }
}
