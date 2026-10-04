package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #1972: a lambda declared as `Function1[Int, ?]` whose body is a
 * nested generic call (`withT { -> v * 2 }`) was rejected with a bogus E0000
 * ("type Int is expected, but type Object is used"). The unbounded `?` result
 * collapsed to `Object` and, unlike a literal `Object` result, did not trigger
 * return-type inference, so the nested call pinned its type argument to Object.
 */
class WildcardLambdaNestedGenericCallSpec extends AbstractShellSpec {

  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def withT[T](body: Function0[T]): T = body.call()
         |  static def main(args: String[]): String {
         |    $body
         |    return "" + f.call(4)
         |  }
         |}
         |""".stripMargin,
      "WildcardLambdaNestedGenericCall.on",
      Array()
    )

  describe("a lambda typed Function1[Int, ?] wrapping a nested generic call") {
    it("compiles and runs with an Int-returning body (#1972)") {
      assert(run("val f: Function1[Int, ?] = (v) -> withT { -> v * 2 }") == Shell.Success("8"))
    }

    it("compiles and runs with a String-returning body (#1972)") {
      assert(run("""val f: Function1[Int, ?] = (v) -> withT { -> "a" + v }""") == Shell.Success("a4"))
    }
  }
}
