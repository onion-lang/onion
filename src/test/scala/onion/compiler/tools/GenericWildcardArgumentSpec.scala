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
    it("rejects a List[Object] into a List[T] in class Agg[T]") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[T] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def addAll(o: List[Object]): void { xs.addAll(o) }
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects a List[U] into a List[T] in class Agg[T, U]") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[T, U] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def addAll(o: List[U]): void { xs.addAll(o) }
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects a List[String] into a List[T] in class Agg[T]") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[T] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def addAll(o: List[String]): void { xs.addAll(o) }
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects a List[Integer] into a List[T] in class Agg[T extends Number]") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[T extends Number] {
          |  val xs: List[T] = new ArrayList[T]()
          |public:
          |  def addAll(o: List[Integer]): void { xs.addAll(o) }
          |}
          |def main(args: String[]): Int { return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("accepts a type variable whose bound chain reaches T") {
      val r = shell.run(
        """import { java.util.* }
          |class Agg[B, T extends B] {
          |  val xs: List[B] = new ArrayList[B]()
          |public:
          |  def addAll(o: List[B]): void { xs.addAll(o) }
          |  def size(): Int { return xs.size() }
          |}
          |def main(args: String[]): Int {
          |  val a = new Agg[String, String]()
          |  a.addAll(["x"])
          |  return a.size()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
}
