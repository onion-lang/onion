package onion.compiler.tools

import onion.tools.Shell

/**
 * A type parameter bounded by another type parameter of the same declaration
 * (`[B, T extends B]`) is accepted where its own bound is expected — the bound
 * declares exactly that relationship (#2259).
 */
class ChainedTypeVariableBoundSpec extends AbstractShellSpec {
  describe("a type variable bounded by another type variable") {
    it("is returned where its bound is expected") {
      val r = shell.run(
        """class Holder[B, T extends B] {
          |  var item: T
          |public:
          |  def this(v: T) { item = v }
          |  def asB(): B { return item }
          |}
          |def main(args: String[]): Int {
          |  val h: Holder[String, String] = new Holder[String, String]("hello")
          |  return h.asB().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
    it("is assignable to a local of its bound's type") {
      val r = shell.run(
        """class Holder[B, T extends B] {
          |  var item: T
          |public:
          |  def this(v: T) { item = v }
          |  def asB(): B { val b: B = item; return b }
          |}
          |def main(args: String[]): Int {
          |  val h: Holder[String, String] = new Holder[String, String]("hi")
          |  return h.asB().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(2) == r)
    }
    it("resolves a member through a real (non-root) bound, two levels up the chain") {
      val r = shell.run(
        """class Holder[B extends CharSequence, T extends B] {
          |  var item: T
          |public:
          |  def this(v: T) { item = v }
          |  def len(): Int { return item.length() }
          |}
          |def main(args: String[]): Int {
          |  val h: Holder[String, String] = new Holder[String, String]("hello")
          |  return h.len()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(5) == r)
    }
  }
  describe("an unrelated type variable must not be accepted via a shared erased bound") {
    it("rejects returning a chained T as a third, unrelated type parameter A (#2259 review repro)") {
      val r = shell.run(
        """class Box[A, B, T extends B] {
          |  var t: T
          |public:
          |  def this(v: T) { t = v }
          |  def wrong(): A { return t }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects assigning U into a sibling T, both bounded by the same B") {
      val r = shell.run(
        """class Box3[B, T extends B, U extends B] {
          |  var t: T
          |  var u: U
          |public:
          |  def this(tv: T, uv: U) { t = tv; u = uv }
          |  def bad(): void { t = u }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects adding a sibling U into a List[T] via a shared bound B") {
      val r = shell.run(
        """import { java.util.* }
          |class Box4[B, T extends B, U extends B] {
          |  var ts: List[T]
          |public:
          |  def this(v: T) { ts = new ArrayList[T](); ts.add(v) }
          |  def poison(u: U): void { ts.add(u) }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects passing a sibling U where a same-class call expects T (second review repro)") {
      val r = shell.run(
        """class Box3b[B, T extends B, U extends B] {
          |  var t: T
          |public:
          |  def this(tv: T) { t = tv }
          |  def set(v: T): void { t = v }
          |  def bad(u: U): void { set(u) }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
    it("rejects returning a List[T] field as List[B] (type arguments are invariant)") {
      val r = shell.run(
        """import { java.util.* }
          |class H[B, T extends B] {
          |  var ts: List[T]
          |public:
          |  def this(l: List[T]) { ts = l }
          |  def asBs(): List[B] { return ts }
          |}
          |def main(args: String[]): void { }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }
  }
  describe("a later type parameter's bound can reference an earlier one") {
    it("substitutes the instantiation's own type arguments into a chained bound (third review repro)") {
      val r = shell.run(
        """class C3[A, B extends A, T extends B] {
          |  var t: T
          |public:
          |  def this(v: T) { t = v }
          |  def get(): T { return t }
          |}
          |def main(args: String[]): Int {
          |  val c = new C3[Object, String, String]("abc")
          |  return c.get().length()
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(3) == r)
    }
  }
}
