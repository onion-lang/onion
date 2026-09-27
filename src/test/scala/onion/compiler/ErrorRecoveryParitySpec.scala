package onion.compiler

import onion.compiler.parser.{JJOnionParser, OnionLexer, OnionParser, ParseException}
import org.scalatest.funspec.AnyFunSpec

/**
 * The handwritten parser's error recovery (`OnionParser.enableErrorRecovery`) is a port of
 * JJOnionParser's: same panic-mode sync points, same deepest-failure diagnostics. This spec
 * pins the port against its specification by running both engines over the same broken
 * programs — position and found-token comparison, not message text (formatting happens
 * later, in Parsing, and is shared).
 *
 * The FIRST error of every program must match the JavaCC engine exactly. The rest of the
 * list is deliberately NOT required to match: JJOnionParser does not reset its lexer-state
 * stack when it panics, so after the first error its resumed parse sees stranded
 * IN_STATEMENT newlines and reports spurious `"\n"`-found errors inside perfectly valid
 * follow-on declarations — sometimes drowning out a later real error entirely. The port
 * resets to the top-level baseline instead, so its subsequent errors are the real ones;
 * those are pinned here as exact expected lists, hand-checked against the source.
 *
 * It drives the parsers directly, NOT through the compile pipeline, so a failure here is a
 * recovery-logic bug and cannot be confused with a Parsing-wiring or hint-classification bug.
 */
class ErrorRecoveryParitySpec extends AnyFunSpec {

  private case class Err(line: Int, column: Int, found: String)

  /** JJOnionParser's collected errors plus, when it still threw, the final ParseException —
   *  converted exactly the way Parsing.addParseException does. */
  private def jjErrors(src: String): List[Err] = {
    val parser = new JJOnionParser(new OnionLexer(src))
    parser.enableErrorRecovery(10)
    val fromException =
      try { parser.unit(); Nil }
      catch {
        case e: ParseException =>
          if (e.currentToken == null) List(Err(1, 1, e.getMessage))
          else {
            val t = e.currentToken.next
            List(Err(t.beginLine, t.beginColumn, t.image))
          }
        case _: Error => Nil // lexer-level java.lang.Error: out of scope here
      }
    val collected = {
      val es = parser.getCollectedErrors()
      (0 until es.size()).toList.map { i =>
        val e = es.get(i)
        Err(e.line, e.column, e.found)
      }
    }
    collected ++ fromException
  }

  private def fastErrors(src: String): List[Err] = {
    val parser = new OnionParser(src)
    parser.enableErrorRecovery(10)
    parser.unit()
    parser.getCollectedErrors.map(e => Err(e.line, e.column, e.found))
  }

  /** Both engines report, and agree exactly on, the first error. */
  private def assertFirstErrorParity(src: String): List[Err] = {
    val jj = jjErrors(src)
    val fast = fastErrors(src)
    assert(jj.nonEmpty, s"JJOnionParser reported no error for:\n$src")
    assert(fast.nonEmpty, s"OnionParser reported no error for:\n$src")
    assert(fast.head == jj.head, s"first error diverged for:\n$src\n  jj=${jj.head}\n  fast=${fast.head}")
    fast
  }

  /** First-error parity with JavaCC, plus the full recovered list pinned exactly. */
  private def assertRecoveredErrors(src: String, expected: List[Err]): Unit = {
    val fast = assertFirstErrorParity(src)
    assert(fast == expected, s"recovered error list changed for:\n$src\n  expected=$expected\n  fast=$fast")
  }

  describe("single-error programs") {
    it("a malformed member declaration inside a class") {
      assertRecoveredErrors(
        """class A {
          |public:
          |  def f(: Int): Int { return 1 }
          |}
          |""".stripMargin,
        List(Err(3, 9, ":")))
    }

    it("a Java-style implements clause") {
      assertFirstErrorParity(
        """class A implements Runnable {
          |}
          |""".stripMargin)
    }

    it("a Python-style lambda") {
      assertFirstErrorParity(
        """class Main {
          |public:
          |  static def main(args: String[]): void {
          |    val f = lambda x: x + 1
          |  }
          |}
          |""".stripMargin)
    }

    it("a C-style parenthesized for loop") {
      assertFirstErrorParity(
        """def main(): void {
          |  for (int i = 0; i < 10; i++) {
          |  }
          |}
          |""".stripMargin)
    }

    it("an unclosed class body (EOF while recovering)") {
      assertFirstErrorParity(
        """class A {
          |public:
          |  def f(): Int { return 1 }
          |""".stripMargin)
    }

    it("an empty file") {
      val jj = jjErrors("")
      val fast = fastErrors("")
      assert(jj.nonEmpty && fast.nonEmpty)
      // Both must land on EOF (empty image); JavaCC reports it at the EOF token's position.
      assert(fast.head.found == jj.head.found, s"jj=${jj.head} fast=${fast.head}")
    }
  }

  describe("multi-error programs (exercising the sync-point skip more than once)") {
    it("two malformed declarations in a row: both real errors are found") {
      assertRecoveredErrors(
        """class A {
          |public:
          |  def f(: Int): Int { return 1 }
          |}
          |class B {
          |public:
          |  def g(: Int): Int { return 2 }
          |}
          |""".stripMargin,
        List(Err(3, 9, ":"), Err(7, 9, ":")))
    }

    it("three broken top-level statements: one error per statement") {
      assertRecoveredErrors(
        """val x = ;
          |val y = ;
          |val z = ;
          |""".stripMargin,
        List(Err(1, 9, ";"), Err(2, 9, ";"), Err(3, 9, ";")))
    }

    it("a broken declaration, a valid one, then a broken one: the valid one stays silent") {
      assertRecoveredErrors(
        """class A {
          |public:
          |  def f(: Int): Int { return 1 }
          |}
          |class Ok {
          |public:
          |  def fine(): Int { return 0 }
          |}
          |interface I {
          |  def m(: Int): Int
          |}
          |""".stripMargin,
        List(Err(3, 9, ":"), Err(10, 9, ":")))
    }

    it("never reports a spurious newline-found error") {
      // The exact failure mode of JJOnionParser's stranded-state recovery, pinned as absent.
      val srcs = List(
        "class A {\npublic:\n  def f(: Int): Int { return 1 }\n}\nclass B {\npublic:\n  def g(): Int { return 2 }\n}\n",
        "val x = ;\nval y = 2\n")
      for (src <- srcs; e <- fastErrors(src))
        assert(!e.found.contains("\n"), s"spurious newline error $e for:\n$src")
    }
  }

  describe("string interpolation") {
    it("a syntax error inside #{...}") {
      val src =
        """class Main {
          |public:
          |  static def main(args: String[]): void {
          |    val s = "x#{1 +}"
          |  }
          |}
          |""".stripMargin
      val jj = jjErrors(src)
      val fast = fastErrors(src)
      assert(jj.nonEmpty, "JJOnionParser reported no error for interpolation break")
      assert(fast.nonEmpty, "OnionParser reported no error for interpolation break")
    }
  }
}
