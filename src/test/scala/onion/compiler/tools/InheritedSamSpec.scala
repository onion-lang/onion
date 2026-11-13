package onion.compiler.tools

import onion.tools.Shell

/**
 * A lambda converts to a functional interface whose single abstract method is inherited:
 * `UnaryOperator` and `BinaryOperator` declare no method of their own, their `apply` comes
 * from `Function`/`BiFunction` (#2844). Before, every such lambda was E0005 `.call(Object)`.
 */
class InheritedSamSpec extends AbstractShellSpec {
  private def run(src: String) = shell.run(src, "None", Array())

  describe("a lambda for an interface with an inherited SAM") {
    it("implements UnaryOperator with an untyped and a typed parameter") {
      assert(Shell.Success("hi!a?") == run(
        """
          |import { java.util.function.UnaryOperator }
          |def main(args: String[]): String {
          |  val f: UnaryOperator[String] = (s) -> s + "!"
          |  val g: UnaryOperator[String] = (s: String) -> s + "?"
          |  return f.apply("hi") + g.apply("a")
          |}
          |""".stripMargin))
    }
    it("implements BinaryOperator over a primitive type argument") {
      assert(Shell.Success(9) == run(
        """
          |import { java.util.function.BinaryOperator }
          |def main(args: String[]): Int {
          |  val m: BinaryOperator[Int] = (a, b) -> if a > b { a } else { b }
          |  return m.apply(3, 9)
          |}
          |""".stripMargin))
    }
    it("passes a lambda where a Java method takes a UnaryOperator") {
      assert(Shell.Success("[ab, cb]") == run(
        """
          |import { java.util.ArrayList }
          |def main(args: String[]): String {
          |  val xs: ArrayList[String] = new ArrayList[String]()
          |  xs.add("a"); xs.add("c")
          |  xs.replaceAll((s) -> s + "b")
          |  return xs.toString()
          |}
          |""".stripMargin))
    }
    it("implements an Onion interface that only extends Function") {
      assert(Shell.Success("LOUD") == run(
        """
          |import { java.util.function.Function }
          |interface Shout conforms Function[String, String] {}
          |def main(args: String[]): String {
          |  val sh: Shout = (s) -> s.toUpperCase()
          |  return sh.apply("loud")
          |}
          |""".stripMargin))
    }
    it("picks the more specific constructor, UnaryOperator over Function, as Java does") {
      assert(Shell.Success("unary:x!/unary:x?") == run(
        """
          |import { java.util.function.Function; java.util.function.UnaryOperator }
          |class Ov {
          |public:
          |  val tag: String
          |  def this(g: Function[String, String]) { this.tag = "function:" + g.apply("x") }
          |  def this(g: UnaryOperator[String]) { this.tag = "unary:" + g.apply("x") }
          |}
          |def main(args: String[]): String {
          |  return new Ov((s) -> s + "!").tag + "/" + new Ov((s: String) -> s + "?").tag
          |}
          |""".stripMargin))
    }
    it("still rejects a lambda of the wrong arity") {
      assert(Shell.Failure(-1) == run(
        """
          |import { java.util.function.UnaryOperator }
          |val f: UnaryOperator[String] = (a, b) -> a
          |""".stripMargin))
    }
  }
}
