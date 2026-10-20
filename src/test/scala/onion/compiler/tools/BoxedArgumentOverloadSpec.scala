package onion.compiler.tools

import onion.tools.Shell

/**
 * A boxed value -- a `List[Int]` element (`xs[0]`, an unannotated `foreach i in xs`) --
 * passed to a Java method overloaded on `int` and `Object` (`StringBuilder.append`) was
 * E0006 "applicable for both append(Int) and append(Object)". Java picks the overload
 * reached without unboxing (JLS 15.12.2 phase 1), so `append(Object)` wins; Onion now
 * does the same. A primitive argument still prefers the primitive overload.
 */
class BoxedArgumentOverloadSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    val sb = new StringBuilder()
         |    $body
         |    return sb.toString()
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  describe("a boxed argument to an int/Object overload") {
    it("a List element read by index") {
      assert(eval("""val xs = [1, 2]
                    |sb.append(xs[0])""".stripMargin) == Shell.Success("1"))
    }
    it("an unannotated foreach variable over a range") {
      assert(eval("""foreach i in 1..3 { sb.append(i) }""") == Shell.Success("123"))
    }
    it("an unannotated foreach variable over a List") {
      assert(eval("""foreach i in [4, 5] { sb.append(i).append(",") }""") == Shell.Success("4,5,"))
    }
    it("a primitive argument still takes the primitive overload") {
      assert(eval("""val n: Int = 7
                    |sb.append(n)""".stripMargin) == Shell.Success("7"))
    }
    it("List.remove with a boxed value removes by value, as in Java") {
      assert(eval("""val xs = new java.util.ArrayList[Int]()
                    |xs.add(10); xs.add(20)
                    |val v = xs[1]
                    |xs.remove(v)
                    |sb.append(xs.toString())""".stripMargin) == Shell.Success("[10]"))
    }
  }
}
