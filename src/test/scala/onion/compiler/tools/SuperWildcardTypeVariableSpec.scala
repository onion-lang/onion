package onion.compiler.tools

import onion.tools.Shell

/**
 * A `Comparator[U]` is not a `Comparator[? super T]` for an unrelated type
 * parameter `U`, so `List[T].sort` rejects it at compile time (#2367).
 */
class SuperWildcardTypeVariableSpec extends AbstractShellSpec {
  private def box(sortWith: String) =
    s"""import { java.util.* }
       |class Box[T, U] {
       |  val xs: List[T] = new ArrayList[T]()
       |public:
       |  def add(t: T): void { xs.add(t) }
       |  $sortWith
       |  def size(): Int { return xs.size() }
       |}
       |def main(args: String[]): Int {
       |  val b = new Box[String, Integer]()
       |  b.add("b"); b.add("a")
       |  return b.size()
       |}
       |""".stripMargin

  describe("a Comparator against a `? super T` parameter inside a generic class") {
    it("rejects a Comparator[U] for an unrelated U") {
      val r = shell.run(box("def sortWith(c: Comparator[U]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("accepts a Comparator[T]") {
      val r = shell.run(box("def sortWith(c: Comparator[T]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Success(2) == r)
    }
    it("accepts a Comparator[Object]") {
      val r = shell.run(box("def sortWith(c: Comparator[Object]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Success(2) == r)
    }
  }
}
