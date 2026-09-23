package onion.compiler.tools

import onion.tools.Shell

/**
 * A zero-field case of a generic ADT enum (e.g. `case Leaf` in
 * `enum Tree[T] { case Leaf; case Node(v: T, l: Tree[T], r: Tree[T]) }`) infers
 * its type argument fine when it is the direct right-hand side of an annotated
 * `val` (`val t: Tree[Int] = new Leaf()`), but previously failed with `E0066`
 * ("raw type ... not allowed") when it appeared as a *nested* constructor
 * argument one level down (`new Node(1, new Leaf(), new Leaf())`) -- even
 * though the pinning annotation (`Tree[Int]`) is already fully to the left of
 * the whole expression (issue #1399).
 *
 * `ConstructionTyping.typeNewObject` types constructor arguments eagerly,
 * before the target constructor overload -- and so the expected parameter
 * types -- are known. A zero-field case has nothing else to pin its type
 * parameter from, so typing it with no expected type reported E0066
 * immediately, which aborted the whole argument list before the existing
 * expected-type retry machinery (`retypeConstructorArguments`, added for
 * #232) ever ran -- that machinery only recovers an argument that typed to
 * the *wrong* type, not one that failed outright.
 */
class NestedZeroFieldGenericArgumentSpec extends AbstractShellSpec {
  private val tree =
    "enum Tree[T] { case Leaf; case Node(v: T, l: Tree[T], r: Tree[T]) }\n" +
      "def sumTree(t: Tree[Int]): Int = select t {\n" +
      "  case n is Node[Int]: n.v() + sumTree(n.l()) + sumTree(n.r())\n" +
      "  case l is Leaf[Int]: 0\n" +
      "}\n"

  it("infers a zero-field generic case's type argument as a direct expected-type target") {
    assert(Shell.Success(0) == shell.run(
      tree + "def main(args: String[]): Int { val t: Tree[Int] = new Leaf(); return sumTree(t) }",
      "None", Array()))
  }

  it("infers a zero-field generic case's type argument nested inside another constructor's argument list") {
    assert(Shell.Success(3) == shell.run(
      tree + "def main(args: String[]): Int { val t: Tree[Int] = new Node(1, new Node(2, new Leaf(), new Leaf()), new Leaf()); return sumTree(t) }",
      "None", Array()))
  }

  it("infers a zero-field generic case nested two levels deep") {
    assert(Shell.Success(6) == shell.run(
      tree +
        "def main(args: String[]): Int { " +
        "val t: Tree[Int] = new Node(1, new Node(2, new Node(3, new Leaf(), new Leaf()), new Leaf()), new Leaf()); " +
        "return sumTree(t) }",
      "None", Array()))
  }

  it("still rejects a genuinely ambiguous nested raw generic constructor call") {
    // Two constructors of the same arity that both accept a raw `Leaf` argument
    // position are ambiguous, so this must still fail rather than guess.
    assert(Shell.Failure(-1) == shell.run(
      "enum Tree[T] { case Leaf; case Node(v: T, l: Tree[T], r: Tree[T]) }\n" +
        "class Ambiguous { public: def this(a: Tree[Int], b: Tree[Int]) {}\n def this(a: Tree[String], b: Tree[String]) {} }\n" +
        "def main(args: String[]): void { val a = new Ambiguous(new Leaf(), new Leaf()) }",
      "None", Array()))
  }
}
