package onion.compiler.tools

import onion.tools.Shell

/**
 * `foreach x in xs { ... }` without a type annotation was a syntax error ("expecting
 * ':'"), even though the element type is right there in the collection's type and the
 * `(k, v)` map form already takes no annotation. The loop variable's type is now inferred:
 * an array's component type, an Iterable's element type, or Object for a raw collection.
 */
class ForeachInferredTypeSpec extends AbstractShellSpec {
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

  describe("foreach without a type annotation") {
    it("infers the element type of a List") {
      assert(eval("""var sum = 0
                    |foreach x in [1, 2, 3] { sum += x }
                    |return "" + sum""".stripMargin) == Shell.Success("6"))
    }
    it("infers the component type of an array") {
      assert(eval("""var out = ""
                    |foreach w in "b a".split(" ") { out = out + w.toUpperCase() }
                    |return out""".stripMargin) == Shell.Success("BA"))
    }
    it("infers a record element type, so its accessors resolve") {
      assert(eval("""var out = ""
                    |foreach p in [new P("x", 1), new P("y", 2)] { out = out + p.name() + p.n() }
                    |return out""".stripMargin, "record P(name: String, n: Int)") == Shell.Success("x1y2"))
    }
    it("infers through a Map's values()") {
      assert(eval("""var sum = 0
                    |foreach v in ["a": 1, "b": 2].values() { sum += v }
                    |return "" + sum""".stripMargin) == Shell.Success("3"))
    }
    it("nests, inferring a List element of a List") {
      assert(eval("""var sum = 0
                    |foreach row in [[1, 2], [3]] { foreach x in row { sum += x } }
                    |return "" + sum""".stripMargin) == Shell.Success("6"))
    }
    it("binds Object for a raw collection") {
      assert(eval("""val raw = (["a", "b"] as java.util.List)
                    |var out = ""
                    |foreach o in raw { out = out + o }
                    |return out""".stripMargin) == Shell.Success("ab"))
    }
    it("lets a lambda capture the loop variable") {
      assert(eval("""var out = ""
                    |foreach x in [1, 2] { val f: () -> Int = () -> x * 10
                    |  out = out + f() }
                    |return out""".stripMargin) == Shell.Success("1020"))
    }
    it("leaves the annotated form unchanged") {
      assert(eval("""var sum = 0
                    |foreach x: Int in [1, 2, 3] { sum += x }
                    |return "" + sum""".stripMargin) == Shell.Success("6"))
    }
    it("still rejects a non-iterable collection") {
      assert(eval("""foreach x in 5 { }
                    |return "" """.stripMargin) == Shell.Failure(-1))
    }
    it("still rejects iterating a Map directly") {
      assert(eval("""foreach e in ["a": 1] { }
                    |return "" """.stripMargin) == Shell.Failure(-1))
    }
  }
}
