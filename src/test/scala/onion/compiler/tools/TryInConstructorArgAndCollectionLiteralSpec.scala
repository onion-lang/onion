package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as a constructor argument, a safe-call
 * (`?.`) argument, a `List`-literal element, or a `Map`-literal key/value
 * crashed the compiler with an `[I0000]` internal error during bytecode
 * verification -- more siblings of issue #669/#745/#747: the JVM clears the
 * operand stack when it dispatches to an exception handler, so a receiver
 * (the `new`'d instance, the safe-call target) or collection reference
 * already pushed via `dup()` before the `try` was silently discarded on the
 * exceptional path, desyncing the merged stack shape from the normal path.
 *
 * `AsmCodeGenerationVisitor.visitNewObject`/`visitSafeCall` now pass their
 * already-pushed receiver as a `pendingTypes` operand to
 * `emitArgumentsWithAdaptation` (mirroring `visitCall`/`visitCallSuper`), and
 * `visitListLiteral`/`visitMapLiteral` spill the collection reference into a
 * local before evaluating any element/key/value that may run its own `try`,
 * mirroring `visitNewArrayWithValues`.
 */
class TryInConstructorArgAndCollectionLiteralSpec extends AbstractShellSpec {
  describe("try/catch expression as a constructor argument") {
    it("does not corrupt the freshly-`new`'d receiver") {
      val result = shell.run(
        """
          |record Pair(a: Int, b: Int)
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Pair = new Pair(try { Integer::parseInt("7") } catch _: Exception { -1 }, 2)
          |    val caught: Pair = new Pair(try { Integer::parseInt("nope") } catch _: Exception { -1 }, 3)
          |    return ok.a() + "|" + ok.b() + "|" + caught.a() + "|" + caught.b()
          |  }
          |}
          |""".stripMargin,
        "TryInConstructorArg.on",
        Array()
      )
      assert(Shell.Success("7|2|-1|3") == result)
    }
  }

  describe("try/catch expression as a super-constructor argument") {
    it("does not corrupt uninitializedThis") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  def this(v: Int) { self.v = v }
          |  val v: Int
          |}
          |class Sub(x: Int) extends Base(try { Integer::parseInt(x + "") } catch _: Exception { -1 }) { }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Sub = new Sub(7)
          |    return ok.v + ""
          |  }
          |}
          |""".stripMargin,
        "TryInSuperConstructorArg.on",
        Array()
      )
      assert(Shell.Success("7") == result)
    }
  }

  describe("try/catch expression as a safe-call argument") {
    it("does not corrupt the safe-call target") {
      val result = shell.run(
        """
          |class Box {
          |public:
          |  def this(v: Int) { self.v = v }
          |  val v: Int
          |  def add(x: Int): Int { return v + x }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b: Box? = new Box(1)
          |    val n: Box? = null
          |    val ok: Int? = b?.add(try { Integer::parseInt("6") } catch _: Exception { -1 })
          |    val caught: Int? = b?.add(try { Integer::parseInt("nope") } catch _: Exception { -1 })
          |    val nullTarget: Int? = n?.add(try { Integer::parseInt("6") } catch _: Exception { -1 })
          |    return ok + "|" + caught + "|" + nullTarget
          |  }
          |}
          |""".stripMargin,
        "TryInSafeCallArg.on",
        Array()
      )
      assert(Shell.Success("7|0|null") == result)
    }
  }

  describe("try/catch expression as a List-literal element") {
    it("does not corrupt the list reference across a caught and an uncaught element") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val xs = [1, try { Integer::parseInt("7") } catch _: Exception { -1 }, 3]
          |    val ys = [try { Integer::parseInt("nope") } catch _: Exception { -1 }, 2]
          |    return xs[0] + "|" + xs[1] + "|" + xs[2] + "|" + ys[0] + "|" + ys[1]
          |  }
          |}
          |""".stripMargin,
        "TryInListLiteral.on",
        Array()
      )
      assert(Shell.Success("1|7|3|-1|2") == result)
    }
  }

  describe("try/catch expression as a Map-literal key or value") {
    it("does not corrupt the map reference when the value may throw") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = ["a": try { Integer::parseInt("7") } catch _: Exception { -1 }, "b": 2]
          |    return m["a"] + "|" + m["b"]
          |  }
          |}
          |""".stripMargin,
        "TryInMapLiteralValue.on",
        Array()
      )
      assert(Shell.Success("7|2") == result)
    }

    it("does not corrupt the map reference when the key may throw") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val m = [(try { "k" + Integer::parseInt("7") } catch _: Exception { "err" }): 1, "b": 2]
          |    return m["k7"] + "|" + m["b"]
          |  }
          |}
          |""".stripMargin,
        "TryInMapLiteralKey.on",
        Array()
      )
      assert(Shell.Success("1|2") == result)
    }
  }
}
