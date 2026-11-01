package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * `T?` is a view of the declared variable `T`, so a `Comparator[T?]` sorts a
 * `List[T?]` (#2399); an unrelated `U?` still does not.
 */
class NullableTypeVariableComparatorSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.code).toSeq
      case _ => Seq()
    }
  }

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
    it("rejects a Comparator[U?] for an unrelated U that would sort at run time (#2527)") {
      val r = shell.run(
        """import { java.util.* }
          |class Nb[T, U] {
          |public:
          |  val xs: List[T?] = new ArrayList[T?]()
          |  def add(x: T?): void { xs.add(x) }
          |  def s(c: Comparator[U?]): void { xs.sort(c) }
          |}
          |def main(args: String[]): Int {
          |  val n = new Nb[String, Integer]()
          |  n.add("b"); n.add("a")
          |  val c: Comparator[Integer?] = (a, b) -> a!!.intValue() - b!!.intValue()
          |  n.s(c)
          |  return 0
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects a non-null Comparator[U] for an unrelated U (#2527)") {
      val r = shell.run(box("def sortWith(c: Comparator[U]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects a non-null Comparator[T] on a List[T?] as no applicable method (E0005)") {
      val codes = errorCodes(
        """import { java.util.* }
          |class Nb[T extends Object] {
          |public:
          |  val xs: List[T?] = new ArrayList[T?]()
          |  def s(c: Comparator[T]): void { xs.sort(c) }
          |}
          |""".stripMargin)
      assert(codes == Seq("E0005"), s"got $codes")
    }
    it("rejects a Comparator[B] for T extends B on a List[T?] as no applicable method (E0005)") {
      val codes = errorCodes(
        """import { java.util.* }
          |class Nb[B extends Object, T extends B] {
          |public:
          |  val xs: List[T?] = new ArrayList[T?]()
          |  def s(c: Comparator[B]): void { xs.sort(c) }
          |}
          |""".stripMargin)
      assert(codes == Seq("E0005"), s"got $codes")
    }
    it("still sorts a List[T?] with a Comparator[T?] and a List[T] with a Comparator[T?]") {
      val r = shell.run(
        """import { java.util.* }
          |class Nb[T extends Comparable[T]] {
          |public:
          |  val xs: List[T?] = new ArrayList[T?]()
          |  val ys: List[T] = new ArrayList[T]()
          |  def add(x: T): void { xs.add(x); ys.add(x) }
          |  def s(c: Comparator[T?]): void { xs.sort(c); ys.sort(c) }
          |}
          |def main(args: String[]): Int {
          |  val n = new Nb[String]()
          |  n.add("b"); n.add("a")
          |  n.s((a, b) -> a!!.compareTo(b!!))
          |  return if n.xs[0] == "a" && n.ys[0] == "a" { 1 } else { 0 }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
}
