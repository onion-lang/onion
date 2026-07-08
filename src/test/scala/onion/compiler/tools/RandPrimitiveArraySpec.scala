package onion.compiler.tools

import onion.tools.Shell

/**
 * Rand::choice/shuffle/sample accept primitive arrays (int[]/long[]/double[]/
 * boolean[]). A primitive array is not assignment-compatible with the generic
 * T[] (Object[]) on the JVM, so these used to fail with E0005; explicit
 * primitive overloads close that gap. shuffle/sample return a boxed list.
 */
class RandPrimitiveArraySpec extends AbstractShellSpec {
  it("shuffles an Int[] into a list of the same size") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val a = new Int[3]
        |   a[0] = 1; a[1] = 2; a[2] = 3
        |   return Rand::shuffle(a).size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("picks an element from an Int[]") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val a = new Int[3]
        |   a[0] = 7; a[1] = 7; a[2] = 7
        |   return Rand::choice(a)
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(7) == result)
  }

  it("samples n distinct elements from a Double[]") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   val a = new Double[4]
        |   a[0] = 1.0; a[1] = 2.0; a[2] = 3.0; a[3] = 4.0
        |   return Rand::sample(a, 2).size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }
}
