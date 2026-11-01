package onion.compiler.tools

import onion.tools.Shell

/**
 * A `Comparator[U]` is not a `Comparator[? super T]` for an unrelated type
 * parameter `U`, so `List[T].sort` rejects it at compile time (#2367), and
 * neither is a `Comparator[? super U]`, nor a `Comparator[U]` for a concrete
 * element type such as `String` (#2397).
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
    it("accepts a null-tolerant Comparator[T?] (#2420)") {
      val r = shell.run(box("def sortWith(c: Comparator[T?]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Success(2) == r)
    }
    it("accepts a null-tolerant Comparator[Object?] (#2420)") {
      val r = shell.run(box("def sortWith(c: Comparator[Object?]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Success(2) == r)
    }
  }
  private def strings(typeParams: String, sortWith: String) =
    s"""import { java.util.* }
       |class Box$typeParams {
       |public:
       |  $sortWith
       |}
       |def main(args: String[]): Int {
       |  return 1
       |}
       |""".stripMargin

  describe("a wildcard or a concrete element type against a `? super` parameter (#2397)") {
    it("rejects a Comparator[? super U] for a List[T] with unrelated U") {
      val r = shell.run(box("def sortWith(c: Comparator[? super U]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("accepts a Comparator[? super T] for a List[T]") {
      val r = shell.run(box("def sortWith(c: Comparator[? super T]): void { xs.sort(c) }"), "None", Array())
      assert(Shell.Success(2) == r)
    }
    it("rejects a Comparator[U] for a List[String]") {
      val r = shell.run(strings("[U]",
        "def sortStrings(c: Comparator[U], ss: List[String]): void { ss.sort(c) }"), "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects a Comparator[U] for a List[String] even when U is bounded") {
      val r = shell.run(strings("[U extends CharSequence]",
        "def sortStrings(c: Comparator[U], ss: List[String]): void { ss.sort(c) }"), "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("accepts a Comparator[U] for a List[T] when T extends U") {
      val r = shell.run(strings("[U, T extends U]",
        "def sortTs(c: Comparator[U], ts: List[T]): void { ts.sort(c) }"), "None", Array())
      assert(Shell.Success(1) == r)
    }
    it("accepts a Comparator[CharSequence] and a Comparator[Object] for a List[String]") {
      val r = shell.run(strings("",
        "def sortStrings(c: Comparator[CharSequence], d: Comparator[Object], ss: List[String]): void { ss.sort(c); ss.sort(d) }"), "None", Array())
      assert(Shell.Success(1) == r)
    }
  }
  describe("an inherited `? super` method reached through `this` in a generic subclass (#2400)") {
    it("sorts with an unqualified sort(c) and with this.sort(c) on ArrayList[S]") {
      val r = shell.run(
        """import { java.util.* }
          |class Sub[S] extends ArrayList[S] {
          |public:
          |  def sortSub(c: Comparator[S]): void { sort(c) }
          |  def sortThis(c: Comparator[S]): void { this.sort(c) }
          |}
          |def main(args: String[]): String {
          |  val s = new Sub[String]()
          |  s.add("z"); s.add("y")
          |  s.sortSub((x: String, y: String) -> x.compareTo(y))
          |  val first = "" + s
          |  s.add("a")
          |  s.sortThis((x: String, y: String) -> x.compareTo(y))
          |  return first + s
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("[y, z][a, y, z]") == r)
    }
    it("calls an inherited sortWith(Comparator[? super T]) of an Onion base class") {
      val r = shell.run(
        """import { java.util.* }
          |class Box[T] {
          |  val items: ArrayList[T]
          |public:
          |  def this { items = new ArrayList[T]() }
          |  def add(t: T): void { items.add(t) }
          |  def sortWith(c: Comparator[? super T]): void { items.sort(c) }
          |  def show(): String = "" + items
          |}
          |class Sub[S] extends Box[S] {
          |public:
          |  def sortSub(c: Comparator[S]): void { sortWith(c) }
          |}
          |def main(args: String[]): String {
          |  val s = new Sub[String]()
          |  s.add("z"); s.add("y")
          |  s.sortSub((x: String, y: String) -> x.compareTo(y))
          |  return s.show()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("[y, z]") == r)
    }
  }
}
