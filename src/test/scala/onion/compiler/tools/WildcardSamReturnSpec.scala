package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for issue #259: a lambda literal passed DIRECTLY to a JDK method whose
 * functional-interface parameter has an upper-bounded wildcard result position
 * (`Function<? super T, ? extends U>`, e.g. CompletableFuture.thenApply,
 * Stream.map, Optional.map) must type-check. Previously the closure's expected
 * return type was left as the raw wildcard `? extends U`, so the lambda body
 * failed with E0000 and the wildcard leaked into the call's result type.
 */
class WildcardSamReturnSpec extends AbstractShellSpec {

  describe("wildcard SAM return position") {
    it("types a lambda passed directly to CompletableFuture.thenApply") {
      val result = shell.run(
        """
          |import { java.util.concurrent.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val cf = CompletableFuture::supplyAsync(() -> 42)
          |    val cf2 = cf.thenApply((x: Integer) -> x + 1)
          |    return cf2.get().toString()
          |  }
          |}
          |""".stripMargin,
        "WildcardThenApply.on",
        Array()
      )
      assert(Shell.Success("43") == result)
    }

    it("types a String-returning lambda for thenApply (not primitive-specific)") {
      val result = shell.run(
        """
          |import { java.util.concurrent.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val cf = CompletableFuture::supplyAsync(() -> 7)
          |    val cf2 = cf.thenApply((x: Integer) -> "val=" + x)
          |    return cf2.get()
          |  }
          |}
          |""".stripMargin,
        "WildcardThenApplyString.on",
        Array()
      )
      assert(Shell.Success("val=7") == result)
    }

    it("types a lambda passed directly to Optional.map") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = Optional::of(10)
          |    val r = o.map((x: Integer) -> x + 5)
          |    return r.get().toString()
          |  }
          |}
          |""".stripMargin,
        "WildcardOptionalMap.on",
        Array()
      )
      assert(Shell.Success("15") == result)
    }

    it("types a lambda passed directly to Stream.map") {
      val result = shell.run(
        """
          |import {
          |  java.util.*
          |  java.util.stream.*
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val list = Arrays::asList(1, 2, 3)
          |    val mapped = list.stream().map((x: Integer) -> x * 2).collect(Collectors::toList())
          |    return mapped.toString()
          |  }
          |}
          |""".stripMargin,
        "WildcardStreamMap.on",
        Array()
      )
      assert(Shell.Success("[2, 4, 6]") == result)
    }

    it("still accepts an explicit Function var (the pre-fix workaround)") {
      val result = shell.run(
        """
          |import {
          |  java.util.concurrent.*
          |  java.util.function.*
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val cf = CompletableFuture::supplyAsync(() -> 42)
          |    val f: Function[Integer, Integer] = (x: Integer) -> x + 1
          |    val cf2 = cf.thenApply(f)
          |    return cf2.get().toString()
          |  }
          |}
          |""".stripMargin,
        "WildcardThenApplyVar.on",
        Array()
      )
      assert(Shell.Success("43") == result)
    }
  }
}
