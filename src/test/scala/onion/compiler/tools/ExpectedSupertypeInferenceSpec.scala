package onion.compiler.tools

import onion.tools.Shell

/**
 * A generic call reads its type arguments off an expected type that is a supertype of
 * the call's result: `val xs: Collection[String] = List::of()` is a `List[String]` (#3017).
 */
class ExpectedSupertypeInferenceSpec extends AbstractShellSpec {
  private def ok(src: String): Unit =
    assert(Shell.Success(0) == shell.run(src, "None", Array()))
  private def rejected(src: String): Unit =
    assert(Shell.Failure(-1) == shell.run(src, "None", Array()))

  describe("an expected supertype of a generic call's result") {
    it("fixes the type argument of List::of() for a Collection target") {
      ok(
        """import { java.util.* }
          |def main(args: String[]): Int {
          |  val xs: Collection[String] = List::of()
          |  return xs.size
          |}
          |""".stripMargin)
    }
    it("fixes it for an Iterable target and a method return") {
      ok(
        """import { java.util.* }
          |def a[T](): Collection[T] = Collections::emptyList()
          |def main(args: String[]): Int {
          |  val ys: Iterable[String] = Collections::emptyList()
          |  val zs: Collection[String] = a()
          |  return zs.size
          |}
          |""".stripMargin)
    }
    it("still rejects a target whose type argument disagrees with the arguments") {
      rejected(
        """import { java.util.* }
          |def main(args: String[]): Int {
          |  val xs: Collection[String] = List::of(1, 2)
          |  return xs.size
          |}
          |""".stripMargin)
    }
    it("still rejects an unrelated target") {
      rejected(
        """import { java.util.* }
          |def main(args: String[]): Int {
          |  val m: Map[String, String] = List::of()
          |  return 0
          |}
          |""".stripMargin)
    }
  }
}
