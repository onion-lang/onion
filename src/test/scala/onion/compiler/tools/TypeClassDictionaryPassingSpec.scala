package onion.compiler.tools

import onion.tools.Shell

/**
 * Type classes: a constrained generic `def sum[T: Numeric](xs: List[T]): T` works
 * polymorphically -- the dictionary for T is resolved from the instance and passed
 * at each call site. Ground (concrete) call types, including boxed and primitive
 * numerics that unify (Int == Integer).
 */
class TypeClassDictionaryPassingSpec extends AbstractShellSpec {
  private val prelude =
    "trait Numeric[T] { def zero(): T\n def one(): T\n def plus(a: T, b: T): T\n def times(a: T, b: T): T }\n" +
    "instance Numeric[Integer] { def zero(): Integer = 0\n def one(): Integer = 1\n def plus(a: Integer, b: Integer): Integer = a + b\n def times(a: Integer, b: Integer): Integer = a * b }\n" +
    "instance Numeric[Long] { def zero(): Long = 0L\n def one(): Long = 1L\n def plus(a: Long, b: Long): Long = a + b\n def times(a: Long, b: Long): Long = a * b }\n" +
    "instance Numeric[Double] { def zero(): Double = 0.0\n def one(): Double = 1.0\n def plus(a: Double, b: Double): Double = a + b\n def times(a: Double, b: Double): Double = a * b }\n" +
    "def sum[T: Numeric](xs: List[T]): T { var acc: T = Numeric[T]::zero()\n foreach x: T in xs { acc = Numeric[T]::plus(acc, x) }\n return acc }\n" +
    "def product[T: Numeric](xs: List[T]): T { var acc: T = Numeric[T]::one()\n foreach x: T in xs { acc = Numeric[T]::times(acc, x) }\n return acc }\n"

  describe("dictionary passing for a constrained generic") {
    it("sums an Integer list") {
      assert(Shell.Success(6) == shell.run(prelude + "def main(args: String[]): Int { return sum([1,2,3]) }", "None", Array()))
    }
    it("sums a Long list") {
      assert(Shell.Success(60L) == shell.run(prelude + "def main(args: String[]): Long { return sum([10L,20L,30L]) }", "None", Array()))
    }
    it("sums a Double list") {
      assert(Shell.Success(7.0) == shell.run(prelude + "def main(args: String[]): Double { return sum([1.5,2.5,3.0]) }", "None", Array()))
    }
    it("multiplies an Integer list") {
      assert(Shell.Success(24) == shell.run(prelude + "def main(args: String[]): Int { return product([1,2,3,4]) }", "None", Array()))
    }
    it("leaves an unconstrained generic untouched") {
      assert(Shell.Success(42) == shell.run(prelude + "def id[T](x: T): T = x\ndef main(args: String[]): Int { return id(42) }", "None", Array()))
    }
  }
}
