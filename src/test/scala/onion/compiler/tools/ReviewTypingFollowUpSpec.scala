package onion.compiler.tools

import onion.tools.Shell

/**
 * Follow-ups from a review of #2271 and #2268:
 *  - A boxed argument to a static method overloaded on int and Object
 *    (`String::valueOf(boxed)`, a user's `static def f(x: Object)` / `f(x: Int)`) was still
 *    E0006; #2271 had fixed only instance calls. It now picks the Object overload too.
 *  - An unannotated `foreach` over a value whose type is a type variable bounded by an
 *    Iterable (`[T extends Iterable[Int]]`) bound the loop variable as Object; it now takes
 *    the bound's element type.
 */
class ReviewTypingFollowUpSpec extends AbstractShellSpec {
  private def eval(body: String, prelude: String = ""): Shell.Result =
    shell.run(
      s"""
         |$prelude
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a boxed argument to a static int/Object overload") {
    it("String::valueOf on a boxed Integer") {
      assert(eval("""val v: java.lang.Integer = 3
                    |return String::valueOf(v)""".stripMargin) == Shell.Success("3"))
    }
    it("a user class's static overloads") {
      assert(eval("""val v: java.lang.Integer = 3
                    |return K::f(v) + K::f(4)""".stripMargin,
        """class K {
          |public:
          |  static def f(x: Object): String = "obj"
          |  static def f(x: Int): String = "int"
          |}""".stripMargin) == Shell.Success("objint"))
    }
  }

  describe("an unannotated foreach over a type-variable collection") {
    it("takes the bound's element type") {
      assert(eval("""return "" + Sum::total([1, 2, 3])""",
        """class Sum {
          |public:
          |  static def total[T extends Iterable[Int]](t: T): Int {
          |    var s = 0
          |    foreach v in t { s += v }
          |    return s
          |  }
          |}""".stripMargin) == Shell.Success("6"))
    }
  }
}
