package onion.compiler.tools

import onion.tools.Shell

/**
 * A bare generic constructor call (`new X()`, no type args, nothing of its own
 * to pin a type parameter from) already infers its type argument from the
 * expected type of a `val`/`var` declaration (`val b: Box[String] = new Empty()`)
 * and from a nested constructor argument's resolved parameter type (#1399). It
 * did not yet do so for the exact same shape in an ordinary *unqualified call*
 * argument position: `describe(new Empty())` where `describe(b: Box[String])`
 * failed outright with `[E0066] raw type Empty is not allowed`, even though the
 * call's own resolved parameter type (`Box[String]`) already pins it the same
 * way the `val` case does.
 *
 * The expected-type retry added for issue #232
 * (`MethodCallTyping.retypeArgumentsForExpected`) only recovers an argument
 * that typed to the *wrong* type; it never got a chance to run here because
 * the initial eager `typedTerms` call was unsuppressed, so `new Empty()`'s
 * E0066 was already reported (and, for a zero-ctor-arg case with no expected
 * type, nothing else pins it) before the retry machinery could try again with
 * the call's resolved parameter type. The fix mirrors
 * `ConstructionTyping.resolveConstructorForMalleableArgs`: suppress the
 * initial attempt when a malleable argument (a bare `new X()` among them) is
 * present, and on failure retype it against a uniquely arity-matched
 * candidate's parameter type before giving up.
 */
class UnqualifiedCallBareGenericArgSpec extends AbstractShellSpec {

  describe("a bare generic new-expression passed to an unqualified call") {
    it("infers its type argument from the call's resolved parameter type") {
      val result = shell.run(
        """
          |class Box[T] { }
          |class Empty[T] extends Box[T] { }
          |def describe(b: Box[String]): String = "ok"
          |def main(args: String[]): String { return describe(new Empty()) }
          |""".stripMargin,
        "BareGenericUnqualifiedArg.on",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("infers a generic ADT enum's zero-field case the same way (CLAUDE.md's Opt[T] example)") {
      val result = shell.run(
        """
          |enum Opt[T] { case Some(value: T); case Nothing }
          |def describe(o: Opt[String]): String = select o {
          |  case s is Some: s.value()
          |  case n is Nothing: "none"
          |}
          |def main(args: String[]): String { return describe(new Nothing()) }
          |""".stripMargin,
        "BareEnumCaseUnqualifiedArg.on",
        Array()
      )
      assert(Shell.Success("none") == result)
    }
  }

  describe("existing behavior is preserved") {
    it("still rejects a bare generic new-expression with nothing at all to pin it") {
      val result = shell.run(
        """
          |class Box[T] { }
          |class Empty[T] extends Box[T] { }
          |def main(args: String[]): Int { val b = new Empty(); return 0 }
          |""".stripMargin,
        "StillRawUnpinned.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still reports a genuine arity mismatch instead of silently matching") {
      val result = shell.run(
        """
          |class Box[T] { }
          |class Empty[T] extends Box[T] { }
          |def describe(b: Box[String]): String = "ok"
          |def main(args: String[]): String { return describe(new Empty(), 5) }
          |""".stripMargin,
        "StillArityMismatch.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still reports an unrelated generic class as not found") {
      val result = shell.run(
        """
          |class Box[T] { }
          |class Wrong[T] { }
          |def describe(b: Box[String]): String = "ok"
          |def main(args: String[]): String { return describe(new Wrong()) }
          |""".stripMargin,
        "StillUnrelatedType.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
