package onion.compiler.tools

import onion.tools.Shell

/**
 * Explicit type arguments for generic methods must box primitive types (Int -> Integer)
 * before building the substitution map, exactly as type inference does.
 *
 * When the user writes `f[Int](xs)`, the substitution `{T -> BasicType.INT}` would
 * substitute `List[T]` -> `List[int]`.  The actual argument `ArrayList[Int]` holds
 * `Integer` (boxed) as its type argument, so `List[int]` vs `List[Integer]` fails the
 * invariant type-argument check even though the code is correct.
 *
 * The fix (GenericMethodTypeArguments.scala, `explicitFromMappedArgs`) is to box each
 * mapped type argument before inserting it into `subst`, mirroring what `Constraints.unify`
 * does in the inference path.
 */
class ExplicitTypeArgPrimitiveSpec extends AbstractShellSpec {

  describe("explicit generic type argument with primitive") {
    it("accepts ArrayList[Int] for List[T] when T is explicitly Int") {
      val result = shell.run(
        """
          | import { java.util.List; java.util.ArrayList }
          | static def printSize[T](lst: List[T]): Int {
          |   return lst.size
          | }
          | static def main(args: String[]): Int {
          |   val xs: ArrayList[Int] = new ArrayList[Int]()
          |   xs.add(1)
          |   xs.add(2)
          |   return printSize[Int](xs)
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(2) == result)
    }

    it("accepts ArrayList[String] for List[T] when T is explicitly String") {
      val result = shell.run(
        """
          | import { java.util.List; java.util.ArrayList }
          | static def first[T](lst: List[T]): T? {
          |   if lst.isEmpty { return null }
          |   return lst[0]
          | }
          | static def main(args: String[]): String {
          |   val xs: ArrayList[String] = new ArrayList[String]()
          |   xs.add("hello")
          |   val r: String? = first[String](xs)
          |   return r ?: "none"
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("accepts ArrayList[Long] for List[T] when T is explicitly Long") {
      val result = shell.run(
        """
          | import { java.util.List; java.util.ArrayList }
          | static def sumLongs[T](lst: List[T], toNum: T -> Long): Long {
          |   var acc: Long = 0L
          |   foreach item: T in lst { acc = acc + toNum(item) }
          |   return acc
          | }
          | static def main(args: String[]): Long {
          |   val xs: ArrayList[Long] = new ArrayList[Long]()
          |   xs.add(10L)
          |   xs.add(20L)
          |   xs.add(30L)
          |   return sumLongs[Long](xs, (x) -> x)
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(60L) == result)
    }
  }
}
