package onion.compiler.tools

import onion.tools.Shell

/**
 * Issue #298: overload resolution must not leak a bounded type parameter's
 * constraint onto a call that selects a *different*, unbounded overload.
 *
 * When one overload is unbounded `[T]` and another is bounded
 * `[T extends Comparable]`, a call matched to the unbounded overload by arity
 * used to still have the bounded overload's `Comparable` constraint applied,
 * failing with E0000 ("Comparable expected, P used"). The bound error was being
 * emitted while collecting applicability over ALL candidates, including the
 * discarded bounded one. The fix suppresses bound errors during collection; the
 * real bound is still enforced when the bounded overload is the one selected.
 *
 * Locale-independent: asserts on Shell.Success return values and
 * Shell.Failure(-1), never on localized message text (CI runs in English).
 */
class OverloadBoundLeakSpec extends AbstractShellSpec {

  describe("issue #298: bounded-overload constraint must not leak") {
    // (a) the arity-selected unbounded overload is checked with its OWN (absent)
    //     bound, so a non-Comparable arg is accepted.
    it("picks the unbounded 2-arg overload for a non-Comparable arg") {
      val result = shell.run(
        """
          |class U {
          |public:
          |  static def pick[T](x: T, y: T): String = "two"
          |  static def pick[T extends java.lang.Comparable](x: T): String = "cmp"
          |}
          |record P(age: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return U::pick(new P(1), new P(2)) }
          |}
          |""".stripMargin,
        "OverloadBoundLeakA.on",
        Array()
      )
      assert(Shell.Success("two") == result)
    }

    // Declaration order must not matter.
    it("is order-independent (bounded overload declared first)") {
      val result = shell.run(
        """
          |class U {
          |public:
          |  static def pick[T extends java.lang.Comparable](x: T): String = "cmp"
          |  static def pick[T](x: T, y: T): String = "two"
          |}
          |record P(age: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return U::pick(new P(1), new P(2)) }
          |}
          |""".stripMargin,
        "OverloadBoundLeakOrder.on",
        Array()
      )
      assert(Shell.Success("two") == result)
    }

    // (b) real-world trigger: Collections.sort with a Comparator on a
    //     non-Comparable element type must select the 2-arg unbounded overload.
    it("sorts a non-Comparable element type via Collections.sort + Comparator") {
      val result = shell.run(
        """
          |import { java.util.* }
          |record P(age: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[P] = [new P(3), new P(1), new P(2)]
          |    Collections::sort(xs, (a: P, b: P) -> a.age() - b.age())
          |    return (xs.get(0) as P).age().toString()
          |  }
          |}
          |""".stripMargin,
        "OverloadBoundLeakSort.on",
        Array()
      )
      assert(Shell.Success("1") == result)
    }

    // (c) real bound enforcement is preserved: a genuine single bounded overload
    //     called with a non-Comparable arg must STILL be rejected.
    it("still rejects a non-Comparable arg for a genuinely bounded overload") {
      val result = shell.run(
        """
          |class U {
          |public:
          |  static def pick[T extends java.lang.Comparable](x: T): String = "cmp"
          |}
          |record P(age: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return U::pick(new P(1)) }
          |}
          |""".stripMargin,
        "OverloadBoundLeakReject.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    // The bound is also enforced when the bounded overload is the arity-selected
    // one among overloads: a non-Comparable 1-arg call must be rejected.
    it("enforces the bound when the bounded overload is arity-selected") {
      val result = shell.run(
        """
          |class U {
          |public:
          |  static def pick[T](x: T, y: T): String = "two"
          |  static def pick[T extends java.lang.Comparable](x: T): String = "cmp"
          |}
          |record P(age: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String { return U::pick(new P(1)) }
          |}
          |""".stripMargin,
        "OverloadBoundLeakArityReject.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    // A bounded overload with a Comparable arg still resolves and runs.
    it("accepts a Comparable arg for the bounded overload") {
      val result = shell.run(
        """
          |class U {
          |public:
          |  static def pick[T](x: T, y: T): String = "two"
          |  static def pick[T extends java.lang.Comparable](x: T): String = "cmp"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return U::pick("hi") }
          |}
          |""".stripMargin,
        "OverloadBoundLeakAccept.on",
        Array()
      )
      assert(Shell.Success("cmp") == result)
    }

    // (d) Collections.sort 1-arg on a Comparable element type still works.
    it("still sorts a Comparable element type via 1-arg Collections.sort") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs: List[Integer] = [3, 1, 2]
          |    Collections::sort(xs)
          |    return xs.get(0).toString()
          |  }
          |}
          |""".stripMargin,
        "OverloadBoundLeakSort1.on",
        Array()
      )
      assert(Shell.Success("1") == result)
    }
  }
}
