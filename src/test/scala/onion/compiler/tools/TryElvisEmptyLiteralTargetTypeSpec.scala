package onion.compiler.tools

import onion.tools.Shell

/**
 * An empty collection literal (`[]` / `[:]`) in a `try`/`catch`/`finally` branch
 * or as the right (fallback) operand of the elvis operator `?:` is target-typed
 * from the expected result type (issue #301, the same class of gap as #300 which
 * covered if/else and select branches). Previously the empty literal there erased
 * to `Object`, so the try-branch join failed with E0000 and the elvis failed with
 * E0001. The expected type is now threaded into the try body, each catch body, and
 * the elvis right operand, mirroring argument and block-trailing positions. When no
 * expected type is present, typing is unchanged, and a genuine type mismatch is
 * still an error. Locale-independent: asserts only on Shell.Success values / codes.
 */
class TryElvisEmptyLiteralTargetTypeSpec extends AbstractShellSpec {
  describe("empty literal in try/catch/finally branches and elvis right operand") {
    it("try/catch: empty list in try branch target-types (a)") {
      assert(Shell.Success(0) == shell.run(
        "def f(): List[Int] = try { [] } catch e: Exception { [1] }\n" +
          "def main(args: String[]): Int { return f().size() }", "None", Array()))
    }

    it("try/catch: empty list in catch branch target-types") {
      // The try body succeeds, so the try value (size 1) is returned; the empty
      // catch branch still had to type-check against List[Int] for this to compile.
      assert(Shell.Success(1) == shell.run(
        "def f(): List[Int] = try { [1] } catch e: Exception { [] }\n" +
          "def main(args: String[]): Int { return f().size() }", "None", Array()))
    }

    it("try/catch/finally: empty branch target-types with a finally block (b)") {
      assert(Shell.Success(0) == shell.run(
        "def f(): List[Int] = try { [] } catch e: Exception { [1] } finally { IO::println(\"fin\") }\n" +
          "def main(args: String[]): Int { return f().size() }", "None", Array()))
    }

    it("try/catch: empty map branch target-types") {
      assert(Shell.Success(0) == shell.run(
        "def f(): Map[String, Int] = try { [:] } catch e: Exception { [\"a\": 1] }\n" +
          "def main(args: String[]): Int { return f().size() }", "None", Array()))
    }

    it("elvis: empty list right operand target-types (c)") {
      assert(Shell.Success(0) == shell.run(
        "def f(o: List[Int]?): List[Int] = o ?: []\n" +
          "def main(args: String[]): Int { return f(null).size() }", "None", Array()))
    }

    it("elvis: empty map right operand target-types") {
      assert(Shell.Success(0) == shell.run(
        "def f(o: Map[String, Int]?): Map[String, Int] = o ?: [:]\n" +
          "def main(args: String[]): Int { return f(null).size() }", "None", Array()))
    }

    it("elvis: non-empty list fallback still works (d)") {
      assert(Shell.Success(1) == shell.run(
        "def f(o: List[Int]?): List[Int] = o ?: [1]\n" +
          "def main(args: String[]): Int { return f(null).size() }", "None", Array()))
      // present value bypasses the fallback
      assert(Shell.Success(2) == shell.run(
        "def f(o: List[Int]?): List[Int] = o ?: [1]\n" +
          "def main(args: String[]): Int { return f([9, 8]).size() }", "None", Array()))
    }

    it("elvis: primitive fallback still works (x ?: 0)") {
      assert(Shell.Success(0) == shell.run(
        "def f(o: Int?): Int = o ?: 0\n" +
          "def main(args: String[]): Int { return f(null) }", "None", Array()))
      assert(Shell.Success(7) == shell.run(
        "def f(o: Int?): Int = o ?: 0\n" +
          "def main(args: String[]): Int { return f(7) }", "None", Array()))
    }

    it("elvis: string fallback still works (s ?: \"\")") {
      assert(Shell.Success(0) == shell.run(
        "def f(s: String?): String = s ?: \"\"\n" +
          "def main(args: String[]): Int { return f(null).length() }", "None", Array()))
    }

    it("chained elvis with empty fallback target-types (a ?: b ?: [])") {
      assert(Shell.Success(0) == shell.run(
        "def f(a: List[Int]?, b: List[Int]?): List[Int] = a ?: b ?: []\n" +
          "def main(args: String[]): Int { return f(null, null).size() }", "None", Array()))
    }

    it("try-with-resources still works (e)") {
      assert(Shell.Success(42) == shell.run(
        "class R <: java.lang.AutoCloseable {\n" +
          "public:\n" +
          "  def this { }\n" +
          "  def close(): void { }\n" +
          "  def value(): Int { return 42 }\n" +
          "}\n" +
          "def main(args: String[]): Int {\n" +
          "  try (val r = new R()) { return r.value() }\n}", "None", Array()))
    }

    it("multi-catch with empty catch branch target-types (e)") {
      // try body succeeds -> returns the try value (size 1); the empty multi-catch
      // branch still type-checks against List[Int].
      assert(Shell.Success(1) == shell.run(
        "def f(): List[Int] = try { [1] } catch e: RuntimeException | IllegalStateException { [] }\n" +
          "def main(args: String[]): Int { return f().size() }", "None", Array()))
    }

    it("a genuine try-branch type mismatch is still an error (f)") {
      assert(Shell.Failure(-1) == shell.run(
        "def f(): List[Int] = try { \"hello\" } catch e: Exception { [1] }\n" +
          "def main(args: String[]): Int { return f().size() }", "None", Array()))
    }

    it("a genuine elvis type mismatch is still an error") {
      assert(Shell.Failure(-1) == shell.run(
        "def f(o: List[Int]?): List[String] = o ?: [\"a\"]\n" +
          "def main(args: String[]): Int { return 0 }", "None", Array()))
    }

    it("no expected type: elvis fallback typing unchanged") {
      assert(Shell.Success(5) == shell.run(
        "def main(args: String[]): Int { val o: Int? = null\n val x = o ?: 5\n return x }", "None", Array()))
    }
  }
}
