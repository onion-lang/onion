package onion.compiler.tools

import onion.tools.Shell

/**
 * `T?` is a view of the declared variable `T`, so a `Comparator[T?]` sorts a
 * `List[T?]` (#2399); an unrelated `U?` still does not.
 */
class NullableTypeVariableComparatorSpec extends AbstractShellSpec {
  private def box(sortWith: String) =
    s"""import { java.util.* }
       |class Box[T, U] {
       |  val xs: List[T?] = new ArrayList[T?]()
       |public:
       |  $sortWith
       |  def size(): Int { return xs.size() }
       |}
       |def main(args: String[]): Int {
       |  return new Box[String, Integer]().size()
       |}
       |""".stripMargin

  describe("a Comparator of a nullable type variable against List[T?].sort") {
    it("accepts a Comparator[T?]") {
      val r = shell.run(box("def sortWith(c: Comparator[T?]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("rejects a Comparator[U?] for an unrelated U") {
      val r = shell.run(box("def sortWith(c: Comparator[U?]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
