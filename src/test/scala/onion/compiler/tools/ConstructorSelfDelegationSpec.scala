package onion.compiler.tools

import onion.tools.Shell

class ConstructorSelfDelegationSpec extends AbstractShellSpec {
  describe("same-class constructor delegation via ': this(args)'") {
    it("delegates to a sibling constructor of the same class") {
      val result = shell.run(
        """
          | class Box {
          |   val w: Int
          |   val h: Int
          | public:
          |   def this(w: Int, h: Int) { self.w = w; self.h = h }
          |   def this(s: Int) : this(s, s) { }
          |   def area(): Int { return w * h }
          |   static def main(args: String[]): String {
          |     return JInteger::toString(new Box(3).area()) + "," + JInteger::toString(new Box(4, 5).area())
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("9,20") == result)
    }

    it("supports a chain of delegations") {
      val result = shell.run(
        """
          | class C {
          |   val a: Int
          |   val b: Int
          |   val c: Int
          | public:
          |   def this(a: Int, b: Int, c: Int) { self.a = a; self.b = b; self.c = c }
          |   def this(a: Int, b: Int) : this(a, b, 0) { }
          |   def this(a: Int) : this(a, 0) { }
          |   def sum(): Int { return a + b + c }
          |   static def main(args: String[]): String {
          |     return JInteger::toString(new C(1).sum()) + "," +
          |            JInteger::toString(new C(1, 2).sum()) + "," +
          |            JInteger::toString(new C(1, 2, 3).sum())
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1,3,6") == result)
    }

    it("does not double-run instance field initializers in the delegating constructor") {
      val result = shell.run(
        """
          | class D {
          |   var log: String = "init;"
          |   val n: Int
          | public:
          |   def this(n: Int) { self.n = n; self.log = self.log + "full;" }
          |   def this() : this(99) { self.log = self.log + "deleg;" }
          |   def show(): String { return log + "n=" + n }
          |   static def main(args: String[]): String {
          |     return new D().show() + "|" + new D(7).show()
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("init;full;deleg;n=99|init;full;n=7") == result)
    }

    it("coexists with a superclass super-init clause") {
      val result = shell.run(
        """
          | class Base {
          |   val x: Int
          | public:
          |   def this(x: Int) { self.x = x }
          |   def getX(): Int { return x }
          | }
          | class Sub : Base {
          |   val y: Int
          | public:
          |   def this(x: Int, y: Int) : (x) { self.y = y }
          |   def this(v: Int) : this(v, v * 2) { }
          |   def total(): Int { return getX() + y }
          |   static def main(args: String[]): String {
          |     return JInteger::toString(new Sub(3).total()) + "," + JInteger::toString(new Sub(3, 4).total())
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("9,7") == result)
    }

    it("reports an error when no sibling constructor matches the delegation args") {
      val result = shell.run(
        """
          | class E {
          |   val a: Int
          | public:
          |   def this(a: Int) { self.a = a }
          |   def this() : this("x") { }
          |   static def main(args: String[]): String { return "unreachable" }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("still supports the plain super-init ': (args)' clause") {
      val result = shell.run(
        """
          | class Animal {
          |   val name: String
          | public:
          |   def this(name: String) { self.name = name }
          |   def getName(): String { return name }
          | }
          | class Dog : Animal {
          | public:
          |   def this() : ("Rex") { }
          |   static def main(args: String[]): String { return new Dog().getName() }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Rex") == result)
    }
  }
}
