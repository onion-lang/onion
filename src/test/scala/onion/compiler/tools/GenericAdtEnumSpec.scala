package onion.compiler.tools

import onion.tools.Shell

/**
 * Generic ADT enums (issue #311): `enum Opt[T] { case Some(value: T); case
 * Nothing }`. Two gaps were in the way — the grammar rejected the type
 * parameters outright, and a type pattern did not recover the scrutinee's type
 * argument, so a matched case's components came back as bare type variables.
 *
 * The type-argument recovery is independent of enums: it applies to any type
 * pattern over a parameterized sealed hierarchy, so the hand-written desugared
 * form is covered too.
 */
class GenericAdtEnumSpec extends AbstractShellSpec {
  private val optEnum =
    """enum Opt[T] {
      |  case Some(value: T)
      |  case Nothing
      |}
      |""".stripMargin

  it("declares a generic ADT enum and narrows a case to the scrutinee's type argument") {
    // "some:hi" requires value() to be a String, not the bare T.
    assert(Shell.Success("some:hi") == shell.run(
      optEnum +
        """def describe(o: Opt[String]): String = select o {
          |  case s is Some: "some:" + s.value()
          |  case n is Nothing: "none"
          |}
          |def main(args: String[]): String { return describe(new Some[String]("hi")) }
          |""".stripMargin, "None", Array()))
  }

  it("matches the singleton case of a generic ADT enum") {
    assert(Shell.Success("none") == shell.run(
      optEnum +
        """def describe(o: Opt[String]): String = select o {
          |  case s is Some: "some:" + s.value()
          |  case n is Nothing: "none"
          |}
          |def main(args: String[]): String { return describe(new Nothing[String]()) }
          |""".stripMargin, "None", Array()))
  }

  it("checks exhaustiveness over a generic sealed hierarchy") {
    // Before, the parameterized scrutinee skipped the sealed check entirely and
    // a missing case compiled, then returned null at runtime.
    assert(Shell.Failure(-1) == shell.run(
      optEnum +
        """def describe(o: Opt[String]): String = select o {
          |  case s is Some: "some"
          |}
          |def main(args: String[]): String { return describe(new Nothing[String]()) }
          |""".stripMargin, "None", Array()))
  }

  it("recovers the type argument for a hand-written generic sealed hierarchy too") {
    assert(Shell.Success("hi") == shell.run(
      """sealed interface Box[T] {}
        |record Full[T](value: T) conforms Box[T]
        |record Empty[T]() conforms Box[T]
        |def get(b: Box[String]): String = select b {
        |  case f is Full: f.value()
        |  case e is Empty: "none"
        |}
        |def main(args: String[]): String { return get(new Full[String]("hi")) }
        |""".stripMargin, "None", Array()))
  }

  it("rejects type parameters on a homogeneous enum") {
    // A plain enum compiles to a java.lang.Enum, which cannot be generic.
    assert(Shell.Failure(-1) == shell.run(
      "enum Color[T] { RED, GREEN }\ndef main(args: String[]): String { return \"x\" }",
      "None", Array()))
  }

  it("still supports a non-generic ADT enum unchanged") {
    assert(Shell.Success("circle") == shell.run(
      """enum Shape {
        |  case Circle(r: Double)
        |  case Square(s: Double)
        |}
        |def name(x: Shape): String = select x {
        |  case c is Circle: "circle"
        |  case s is Square: "square"
        |}
        |def main(args: String[]): String { return name(new Circle(1.0)) }
        |""".stripMargin, "None", Array()))
  }
}
