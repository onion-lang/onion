package onion.compiler.tools

import onion.tools.Shell

/**
 * A type pattern nests inside a destructuring pattern (issue #299):
 * `case Add(l, n is Num)`. Type patterns already worked at the top level of a
 * `case`, and record patterns already nested, so `is` not nesting was an
 * asymmetry with no workaround for a non-record component.
 *
 * The nested binding must be usable at the *narrowed* type — the component is
 * declared at the wider type, so the binding's access path needs a cast; without
 * it the class verifier rejects the method.
 */
class NestedTypePatternSpec extends AbstractShellSpec {
  private val exprAdt =
    """sealed interface E {}
      |record Num(v: Int) conforms E
      |record Add(l: E, r: E) conforms E
      |""".stripMargin

  it("matches a nested type pattern and binds it at the narrowed type") {
    // n.v() only resolves if n is bound as Num, not as the declared E.
    assert(Shell.Success("add-num:2") == shell.run(
      exprAdt +
        """def f(e: E): String = select e {
          |  case Add(l, n is Num): "add-num:" + n.v()
          |  case Add(l, r): "add-other"
          |  case n is Num: "num"
          |}
          |def main(args: String[]): String { return f(new Add(new Num(1), new Num(2))) }
          |""".stripMargin, "None", Array()))
  }

  it("falls through when the nested type does not match") {
    assert(Shell.Success("add-other") == shell.run(
      exprAdt +
        """def f(e: E): String = select e {
          |  case Add(l, n is Num): "add-num"
          |  case Add(l, r): "add-other"
          |  case n is Num: "num"
          |}
          |def main(args: String[]): String {
          |  return f(new Add(new Num(1), new Add(new Num(2), new Num(3))))
          |}
          |""".stripMargin, "None", Array()))
  }

  it("narrows a component whose declared type is not a record") {
    // No nested *constructor* pattern can express this, so it is the case the
    // workaround (`case Wrap(x) when x is String`) existed for.
    assert(Shell.Success("str:HI") == shell.run(
      """record Wrap(x: Object)
        |def g(w: Wrap): String = select w {
        |  case Wrap(s is String): "str:" + s.toUpperCase()
        |  case Wrap(o): "other"
        |}
        |def main(args: String[]): String { return g(new Wrap("hi")) }
        |""".stripMargin, "None", Array()))
  }

  it("takes the later case when the non-record component has another type") {
    assert(Shell.Success("other") == shell.run(
      """record Wrap(x: Object)
        |def g(w: Wrap): String = select w {
        |  case Wrap(s is String): "str"
        |  case Wrap(o): "other"
        |}
        |def main(args: String[]): String { return g(new Wrap(42)) }
        |""".stripMargin, "None", Array()))
  }

  it("still supports the plain and nested-record forms alongside it") {
    assert(Shell.Success("num:7") == shell.run(
      exprAdt +
        """def f(e: E): String = select e {
          |  case Add(Num(a), Num(b)): "both:" + (a + b)
          |  case n is Num: "num:" + n.v()
          |  else: "other"
          |}
          |def main(args: String[]): String { return f(new Num(7)) }
          |""".stripMargin, "None", Array()))
  }

  it("rejects a nested type pattern naming a non-object type") {
    assert(Shell.Failure(-1) == shell.run(
      """record Wrap(x: Object)
        |def g(w: Wrap): String = select w {
        |  case Wrap(s is NoSuchType): "str"
        |  else: "other"
        |}
        |def main(args: String[]): String { return g(new Wrap("hi")) }
        |""".stripMargin, "None", Array()))
  }
}
