package onion.compiler.tools

import onion.tools.Shell

/**
 * A `try`/`catch` expression used as a non-first call argument (or nested
 * inside one) used to crash the compiler with an `[I0000]` internal error
 * during bytecode verification (issue #669): the JVM clears the operand
 * stack when it dispatches to an exception handler, so any argument already
 * pushed before the `try` was silently discarded, leaving mismatched
 * stackmap frames at the call site.
 *
 * `AsmCodeGenerationVisitor.emitArgumentsWithAdaptation` now spills
 * already-pushed operands (including a call/constructor receiver) into
 * locals before evaluating an argument that may run its own `try`, and
 * reloads them afterward.
 */
class TryAsCallArgumentSpec extends AbstractShellSpec {
  describe("try/catch expression as a call argument") {
    it("does not corrupt sibling arguments inside a select-on-enum case arm (issue #669)") {
      val result = shell.run(
        """
          |enum Fam { Sub, Trans }
          |enum Kind(fam: Fam) {
          |  A(Fam::Sub),
          |  B(Fam::Sub),
          |  C(Fam::Trans)
          |}
          |
          |def shiftUp(text: String, n: Int): String = text + "+" + n
          |def echo(text: String): String = text
          |
          |class Reg {
          |public:
          |  def apply(k: Kind, text: String, key: String): String = select k {
          |    case Kind::A: shiftUp(text, try { Integer::parseInt(key) } catch _: Exception { -1 })
          |    case Kind::B: echo(text)
          |    case Kind::C: shiftUp(text, try { Integer::parseInt(key) } catch _: Exception { -2 })
          |  }
          |}
          |
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val reg: Reg = new Reg()
          |    val r1: String = reg.apply(Kind::A, "hi", "7")
          |    val r2: String = reg.apply(Kind::A, "hi", "xx")
          |    val r3: String = reg.apply(Kind::B, "hi", "z")
          |    val r4: String = reg.apply(Kind::C, "hi", "yy")
          |    return r1 + "|" + r2 + "|" + r3 + "|" + r4
          |  }
          |}
          |""".stripMargin,
        "TryAsCallArgumentSelect.on",
        Array()
      )
      assert(Shell.Success("hi+7|hi+-1|hi|hi+-2") == result)
    }

    it("does not corrupt the receiver of an instance method call") {
      val result = shell.run(
        """
          |class Combiner {
          |public:
          |  def combine(prefix: String, n: Int): String = prefix + "#" + n
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c: Combiner = new Combiner()
          |    val ok: String = c.combine("P", try { Integer::parseInt("42") } catch _: Exception { -1 })
          |    val bad: String = c.combine("P", try { Integer::parseInt("nope") } catch _: Exception { -1 })
          |    return ok + "|" + bad
          |  }
          |}
          |""".stripMargin,
        "TryAsCallArgumentReceiver.on",
        Array()
      )
      assert(Shell.Success("P#42|P#-1") == result)
    }

    it("preserves left-to-right argument evaluation order around the spill") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static var log: String = ""
          |
          |  static def note(tag: String): String {
          |    Test::log = Test::log + tag
          |    return tag
          |  }
          |
          |  static def boom(): String {
          |    throw new Exception("boom")
          |  }
          |
          |  static def combine3(a: String, b: String, c: String): String = a + b + c
          |
          |  static def main(args: String[]): String {
          |    val r: String = combine3(
          |      Test::note("A"),
          |      try { Test::note("T"); Test::boom() } catch _: Exception { Test::note("C") },
          |      Test::note("D")
          |    )
          |    return Test::log + ":" + r
          |  }
          |}
          |""".stripMargin,
        "TryAsCallArgumentOrder.on",
        Array()
      )
      assert(Shell.Success("ATCD:ACD") == result)
    }
  }
}
