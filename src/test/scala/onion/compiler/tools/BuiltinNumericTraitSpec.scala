package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell
import java.io.StringReader

/**
 * The built-in `onion.typeclass.Numeric` type class (RFC #2760, stage 1a): a script
 * imports the trait and gets instances for Int, Long, Double, Float, BigInteger and
 * BigDecimal with no `instance` declaration of its own. `Stats` is unchanged here.
 */
class BuiltinNumericTraitSpec extends AbstractShellSpec {
  private val prelude =
    """import { onion.typeclass.Numeric; java.math.BigDecimal; java.math.BigInteger }
      |def total[T: Numeric](xs: List[T]): T {
      |  var acc = Numeric[T]::zero()
      |  foreach x in xs { acc = Numeric[T]::plus(acc, x) }
      |  return acc
      |}
      |def product[T: Numeric](xs: List[T]): T {
      |  var acc = Numeric[T]::one()
      |  foreach x in xs { acc = Numeric[T]::times(acc, x) }
      |  return acc
      |}
      |""".stripMargin

  private def run(body: String): Shell.Result = shell.run(prelude + body.stripMargin, "None", Array())

  private def errors(src: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors
      case _ => Seq.empty
    }
  }

  describe("the shipped instances") {
    it("sums a List[Int] exactly, as an Int") {
      assert(Shell.Success(7) == run("def main(args: String[]): Int { return total([3, 4]) }"))
    }
    it("sums a List[Long] as a Long") {
      assert(Shell.Success(10000000000L) == run("def main(args: String[]): Long { return total([4000000000L, 6000000000L]) }"))
    }
    it("sums a List[Double]") {
      assert(Shell.Success(3.5) == run("def main(args: String[]): Double { return total([1.5, 2.0]) }"))
    }
    it("sums a List[Float] in float precision") {
      assert(Shell.Success("0.3") == run("""def main(args: String[]): String { return "" + total([0.1f, 0.2f]) }"""))
    }
    it("sums a List[BigDecimal] without rounding") {
      assert(Shell.Success("0.3") == run(
        """def main(args: String[]): String { return "" + total([new BigDecimal("0.1"), new BigDecimal("0.2")]) }"""))
    }
    it("sums a List[BigInteger] past Long's range") {
      assert(Shell.Success("10000000000000000001") == run(
        """def main(args: String[]): String { return "" + total([new BigInteger("10000000000000000000"), BigInteger::ONE]) }"""))
    }
    it("multiplies with one and times") {
      assert(Shell.Success(24) == run("def main(args: String[]): Int { return product([2, 3, 4]) }"))
    }
    it("gives zero for an empty list") {
      assert(Shell.Success(0) == run("def main(args: String[]): Int { return total(new java.util.ArrayList[Int]()) }"))
    }
    it("throws on Int overflow instead of wrapping, as sumInt does") {
      assert(Shell.Success("overflow") == run(
        """def main(args: String[]): String {
          |  try { return "" + total([2147483647, 1]) } catch e: ArithmeticException { return "overflow" }
          |}""".stripMargin))
    }
    it("throws on Long overflow instead of wrapping") {
      assert(Shell.Success("overflow") == run(
        """def main(args: String[]): String {
          |  try { return "" + product([9223372036854775807L, 2L]) } catch e: ArithmeticException { return "overflow" }
          |}""".stripMargin))
    }
  }

  describe("beside the shipped instances") {
    it("a user adds an instance for another type") {
      assert(Shell.Success(7) == run(
        """instance Numeric[Short] {
          |  def zero(): Short = 0 as Short
          |  def one(): Short = 1 as Short
          |  def plus(a: Short, b: Short): Short = (a + b) as Short
          |  def times(a: Short, b: Short): Short = (a * b) as Short
          |}
          |def main(args: String[]): Int {
          |  val ss: List[Short] = [2 as Short, 5 as Short]
          |  return total(ss) as Int
          |}""".stripMargin))
    }
    it("a Java-style caller takes a named instance") {
      assert(Shell.Success(42) == run("def main(args: String[]): Int { return Numeric::forInt().plus(40, 2) }"))
    }
    it("a type with no instance is still a compile error, not a runtime failure") {
      val errs = errors(prelude + "def main(args: String[]): void { println(total([\"a\"])) }")
      assert(errs.nonEmpty)
    }
  }

  describe("effects") {
    it("a tool that sums through the built-in instance needs only the effects it uses") {
      val errs = errors(prelude + "tool add(): void requires { console } { println(total([1, 2, 3])) }")
      assert(!errs.exists(_.errorCode.contains("E0077")), errs.map(_.message).mkString("\n"))
      assert(errs.isEmpty, errs.map(_.message).mkString("\n"))
    }
  }

  describe("a script's own trait named Numeric") {
    it("still means the script's trait, with the built-in one not imported") {
      assert(Shell.Success(3) == shell.run(
        """trait Numeric[T] { def zero(): T
          | def plus(a: T, b: T): T }
          |instance Numeric[Integer] { def zero(): Integer = 0
          | def plus(a: Integer, b: Integer): Integer = a + b }
          |def total[T: Numeric](xs: List[T]): T {
          |  var acc = Numeric[T]::zero()
          |  foreach x in xs { acc = Numeric[T]::plus(acc, x) }
          |  return acc
          |}
          |def main(args: String[]): Int { return total([1, 2]) }""".stripMargin, "None", Array()))
    }
  }
}
