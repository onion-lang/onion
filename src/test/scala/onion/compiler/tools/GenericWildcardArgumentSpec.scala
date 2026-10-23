package onion.compiler.tools

import onion.tools.Shell

/**
 * A `List[T]` is accepted where the JDK declares `Collection<? extends E>`,
 * even when `E` is the class's own type parameter (#2341).
 */
class GenericWildcardArgumentSpec extends AbstractShellSpec {
  describe("a call argument against a `? extends T` parameter inside a generic class") {
    it("accepts a List[T] for addAll on a List[T]") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[T] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def addAll(o: List[T]): void { xs.addAll(o) }
          |  def size(): Int { return xs.size() }
          |}
          |def main(args: String[]): Int {
          |  val a = new Agg[String]()
          |  a.addAll(["x", "y", "z"])
          |  return a.size()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
    it("still rejects a List of an unrelated concrete type") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[T extends Number] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def addAll(o: List[String]): void { xs.addAll(o) }
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
}
