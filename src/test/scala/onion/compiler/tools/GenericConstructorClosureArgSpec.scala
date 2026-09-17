package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic class constructor whose argument list includes a closure
 * (`{ param -> body }` syntax) must not trigger an ICE.  The parser wraps
 * the closure in a BlockExpression, and resolveConstructorForClosures must
 * extract the arity from that wrapper rather than blindly casting to
 * ClosureExpression (issue #1307).
 */
class GenericConstructorClosureArgSpec extends AbstractShellSpec {
  private val wrapper =
    """class Wrapper[T] {
      |  val item: T
      |  val scorer: Function1[T, Int]
      |public:
      |  def this(item: T, scorer: Function1[T, Int]) {
      |    this.item = item
      |    this.scorer = scorer
      |  }
      |  def score(): Int = scorer(item)
      |}
      |""".stripMargin

  it("compiles and runs a generic constructor call with a closure argument") {
    assert(Shell.Success(5) == shell.run(
      wrapper + "def main(args: String[]): Int { val w = new Wrapper[String](\"hello\", { s -> s.length() })\nreturn w.score() }",
      "None", Array()))
  }

  it("compiles a two-type-param generic constructor with two closure arguments") {
    val twoParam =
      """class Mapper[A, B] {
        |  val fn: Function1[A, B]
        |  val gn: Function1[B, Int]
        |public:
        |  def this(fn: Function1[A, B], gn: Function1[B, Int]) {
        |    this.fn = fn
        |    this.gn = gn
        |  }
        |  def apply(a: A): Int = gn(fn(a))
        |}
        |""".stripMargin
    assert(Shell.Success(3) == shell.run(
      twoParam + "def main(args: String[]): Int { val m = new Mapper[String, String]({ s -> s.toUpperCase() }, { s -> s.length() })\nreturn m.apply(\"abc\") }",
      "None", Array()))
  }
}
