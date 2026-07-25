package onion.compiler.tools

import onion.tools.Shell

/**
 * `Rand::choice`/`shuffle`/`sample` take a `List`, and a list of primitives
 * works: an `Int` boxes into the element type, so `[1, 2, 3]` is a
 * `List[Integer]` the generic signature accepts.
 *
 * This replaces the primitive-array overloads (`int[]`, `long[]`, `double[]`,
 * `boolean[]`), which existed only because a primitive array is not
 * assignment-compatible with the generic `T[]` on the JVM. With the stdlib
 * exposing lists, one generic signature covers every element type.
 */
class RandPrimitiveListSpec extends AbstractShellSpec {
  it("shuffles a list of Int into a list of the same size") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   return Rand::shuffle([1, 2, 3]).size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }

  it("picks an element from a list of Int") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   return Rand::choice([7, 7, 7])
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(7) == result)
  }

  it("samples n elements from a list of Double") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   return Rand::sample([1.5, 2.5, 3.5, 4.5], 2).size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }

  it("shuffles a list of Long") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   return Rand::shuffle([1L, 2L]).size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(2) == result)
  }

  it("picks from a list of Boolean") {
    val result = shell.run(
      """
        | static def main(args: String[]): Boolean {
        |   return Rand::choice([true, true])
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(true) == result)
  }

  it("shuffles a list of String") {
    val result = shell.run(
      """
        | static def main(args: String[]): Int {
        |   return Rand::shuffle(["a", "b", "c"]).size
        | }
      """.stripMargin, "None", Array())
    assert(Shell.Success(3) == result)
  }
}
