package onion.compiler.tools

import onion.tools.Shell

/**
 * The primary/secondary constructor model.
 *
 * A class with a primary constructor -- a parameter list, or arguments on `extends`, or
 * both -- routes every construction through it: the primary owns the superclass call and
 * stores the `val`/`var` parameter fields, and every `def this` delegates to it with
 * `: this(...)`. A class with neither has no primary, and its `def this` constructors
 * call the superclass no-arg constructor directly, as they always did.
 *
 * Two of the cases here were silent wrong answers before the model existed, and they are
 * the reason it does: `class P(val x: Int) { def this { } }` compiled and gave
 * `new P().x == 0`, and `class Q(val x: Int) { var total: Int = x * 2 }` gave
 * `total == 0` because the initializer ran before the field was stored.
 */
class ConstructorDelegationSpec extends AbstractShellSpec {

  describe("delegating to the primary") {
    it("a secondary reaches the primary's field stores") {
      val result = shell.run(
        """
          |class P(val x: Int, val y: Int) {
          |public:
          |  def this(x: Int) : this(x, 0) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { val p = new P(5); return "" + p.x + "," + p.y }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("5,0") == result)
    }

    it("secondaries may chain, as long as the chain ends at the primary") {
      val result = shell.run(
        """
          |class P(val x: Int, val y: Int, val z: Int) {
          |public:
          |  def this(x: Int, y: Int) : this(x, y, 0) { }
          |  def this(x: Int) : this(x, 0) { }
          |  def this : this(9) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { val p = new P(); return "" + p.x + p.y + p.z }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("900") == result)
    }

    it("the primary passes its arguments to the superclass; a secondary reaches them through it") {
      val result = shell.run(
        """
          |class Base {
          |  val tag: String
          |public:
          |  def this(tag: String) { self.tag = tag }
          |  def getTag(): String = tag
          |}
          |class Sub(tag: String, val n: Int) extends Base(tag) {
          |public:
          |  def this(n: Int) : this("default", n) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { val s = new Sub(3); return s.getTag() + s.n }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("default3") == result)
    }

    it("a generic primary can be delegated to with a boxed primitive and with null") {
      // Self-delegation resolves against the class's own erased constructor, so `Int`
      // has to box into `T` on the way -- the same path `new Box[Int](7)` takes.
      val result = shell.run(
        """
          |class Box[T](val v: T) {
          |public:
          |  def this : this(null) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Box[Int](7)
          |    val b = new Box[String]()
          |    return "" + a.v + "," + b.v
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("7,null") == result)
    }
  }

  describe("field initializers") {
    it("run after the primary's parameter fields are stored") {
      // Was 0: the initializer was spliced in *before* `this.x = x`, so `x` -- resolved
      // to the field, since initializers are typed without the constructor's parameters
      // in scope -- was still at its default.
      val result = shell.run(
        """
          |class Q(val x: Int) {
          |public:
          |  var total: Int = x * 2
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "" + new Q(21).total }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("42") == result)
    }

    it("run exactly once, in the primary, even when construction goes through a secondary") {
      val result = shell.run(
        """
          |class Counter {
          |public:
          |  static var inits: Int = 0
          |}
          |class R(val x: Int) {
          |public:
          |  var seen: Int = { Counter::inits = Counter::inits + 1; x }
          |  def this : this(1) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { new R(); new R(2); return "" + Counter::inits }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("2") == result)
    }
  }

  describe("classes without a primary") {
    it("keep the old behaviour: def this calls the superclass no-arg constructor") {
      val result = shell.run(
        """
          |class L {
          |public:
          |  val v: Int
          |  def this(v: Int) { this.v = v }
          |  def this : this(9) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return "" + new L().v + new L(4).v }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("94") == result)
    }

    it("a class with neither parameters nor extends(args) still gets an implicit no-arg constructor") {
      val result = shell.run(
        """
          |class E { public: def hi(): String = "hi" }
          |class Test {
          |public:
          |  static def main(args: String[]): String { return new E().hi() }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("hi") == result)
    }

    it("a class with only def this(x) does NOT gain a public no-arg constructor") {
      // If every class silently had a no-arg primary, `new C()` would start compiling for
      // a class that only ever offered `new C(1)`.
      val result = shell.run(
        """
          |class C {
          |public:
          |  def this(x: Int) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { new C(); return "" }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == result)
    }
  }

  describe("closures in delegation arguments") {
    it("are allowed: they capture this and run later, when the object exists") {
      val result = shell.run(
        """
          |class W(val f: () -> Int) {
          |public:
          |  def this : this(() -> 5) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { val g = new W().f; return "" + g() }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("5") == result)
    }
  }
}
