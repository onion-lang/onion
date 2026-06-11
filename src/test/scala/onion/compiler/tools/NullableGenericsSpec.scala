package onion.compiler.tools

import onion.tools.Shell

/**
 * Nullable-aware generics: a bare [T] ranges over nullable types
 * (Box[String?] is legal), while an explicit bound [T extends B] keeps T
 * non-null and rejects nullable type arguments.
 */
class NullableGenericsSpec extends AbstractShellSpec {

  describe("Nullable type arguments") {
    it("accepts T? as a type argument of a bare type parameter") {
      val result = shell.run(
        """
          |class Box[T] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def get(): T { return this.item }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val some: String? = "hello"
          |    val box = new Box[String?](some)
          |    val out: String? = box.get()
          |    val none: String? = null
          |    val empty = new Box[String?](none)
          |    return "" + out + "," + empty.get()
          |  }
          |}
          |""".stripMargin,
        "NullableArg.on",
        Array()
      )
      assert(Shell.Success("hello,null") == result)
    }

    it("stores null through a mutable T slot instantiated with String?") {
      val result = shell.run(
        """
          |class Cell[T] {
          |  var item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def set(value: T): void { this.item = value }
          |  def get(): T { return this.item }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val start: String? = "start"
          |    val cell = new Cell[String?](start)
          |    val none: String? = null
          |    cell.set(none)
          |    return "" + cell.get()
          |  }
          |}
          |""".stripMargin,
        "NullableCellSet.on",
        Array()
      )
      assert(Shell.Success("null") == result)
    }

    it("collapses T? when T is already nullable") {
      val result = shell.run(
        """
          |class Holder[T] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def peek(): T? { return this.item }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val some: String? = "deep"
          |    val holder = new Holder[String?](some)
          |    val out: String? = holder.peek()
          |    return "" + out
          |  }
          |}
          |""".stripMargin,
        "NullableCollapse.on",
        Array()
      )
      assert(Shell.Success("deep") == result)
    }

    it("infers T as String? when arguments mix String and String?") {
      val result = shell.run(
        """
          |class Util {
          |public:
          |  static def first[T](a: T, b: T): T { return a }
          |  static def main(args: String[]): String {
          |    val maybe: String? = "m"
          |    val r = Util::first(maybe, "solid")
          |    val r2 = Util::first("solid", maybe)
          |    return "" + r + "," + r2
          |  }
          |}
          |""".stripMargin,
        "NullableInference.on",
        Array()
      )
      assert(Shell.Success("m,solid") == result)
    }
  }

  describe("Non-null bounds") {
    it("rejects String? for [T extends Comparable]") {
      val result = shell.run(
        """
          |class Box[T extends Comparable] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val maybe: String? = "x"
          |    val box = new Box[String?](maybe)
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "NonNullBound.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects String? for [T extends Object] despite the Object top-type rule") {
      val result = shell.run(
        """
          |class Box[T extends Object] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val maybe: String? = "x"
          |    val box = new Box[String?](maybe)
          |    return "no"
          |  }
          |}
          |""".stripMargin,
        "ObjectBound.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still accepts non-null type arguments for bounded parameters") {
      val result = shell.run(
        """
          |class Box[T extends Comparable] {
          |  val item: T
          |public:
          |  def this(item: T) { this.item = item }
          |  def get(): T { return this.item }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val box = new Box[String]("bounded")
          |    return box.get()
          |  }
          |}
          |""".stripMargin,
        "BoundedOk.on",
        Array()
      )
      assert(Shell.Success("bounded") == result)
    }
  }

  describe("Nullable types in function values") {
    it("accepts String? as a lambda parameter type") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val f: Function1[String?, String] = (x: String?) -> "v=" + x
          |    val none: String? = null
          |    return f.call("a") + "," + f.call(none)
          |  }
          |}
          |""".stripMargin,
        "NullableLambda.on",
        Array()
      )
      assert(Shell.Success("v=a,v=null") == result)
    }
  }
}
