package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #307: the least-upper-bound of two sibling branch
 * types (sharing a common superclass/interface but neither a supertype of the
 * other) must be their nearest common ancestor, not Object, so members declared
 * on that ancestor stay callable after a branch merge.
 */
class BranchLubCommonAncestorSpec extends AbstractShellSpec {
  describe("branch LUB of siblings walks the class hierarchy") {
    it("a) two subclasses of a common superclass merge to that superclass") {
      val result = shell.run(
        """
          | class Animal {
          | public:
          |   def this {}
          |   def sound(): String = "..."
          | }
          | class Dog : Animal {
          | public:
          |   def this {}
          |   override def sound(): String = "woof"
          | }
          | class Cat : Animal {
          | public:
          |   def this {}
          |   override def sound(): String = "meow"
          | }
          | static def main(args: String[]): String {
          |   val a = if true { new Dog() } else { new Cat() }
          |   return a.sound()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("woof") == result)
    }

    it("b) two classes sharing one interface merge to that interface") {
      val result = shell.run(
        """
          | interface Speaker {
          |   def speak(): String
          | }
          | class A <: Speaker {
          | public:
          |   def this {}
          |   def speak(): String = "A"
          | }
          | class B <: Speaker {
          | public:
          |   def this {}
          |   def speak(): String = "B"
          | }
          | static def main(args: String[]): String {
          |   val x = if true { new A() } else { new B() }
          |   return x.speak()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("A") == result)
    }

    it("c) when one branch type is a supertype of the other, that supertype is kept") {
      val result = shell.run(
        """
          | class Animal {
          | public:
          |   def this {}
          |   def sound(): String = "animal"
          | }
          | class Dog : Animal {
          | public:
          |   def this {}
          |   override def sound(): String = "woof"
          | }
          | static def main(args: String[]): String {
          |   val a = if true { new Dog() } else { new Animal() }
          |   return a.sound()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("woof") == result)
    }

    it("d) a select with sibling branches also gets the common ancestor") {
      val result = shell.run(
        """
          | class Animal {
          | public:
          |   def this {}
          |   def sound(): String = "..."
          | }
          | class Dog : Animal {
          | public:
          |   def this {}
          |   override def sound(): String = "woof"
          | }
          | class Cat : Animal {
          | public:
          |   def this {}
          |   override def sound(): String = "meow"
          | }
          | static def main(args: String[]): String {
          |   val n = 1
          |   val a = select n {
          |     case 1: new Dog()
          |     else: new Cat()
          |   }
          |   return a.sound()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("woof") == result)
    }

    it("e) ambiguous siblings (two unrelated shared interfaces) stay Object") {
      // No unique LUB without intersection types: the merge is Object, so a
      // method only declared on the interfaces is not resolvable (E0005).
      val result = shell.run(
        """
          | interface I1 { def m1(): String }
          | interface I2 { def m2(): String }
          | class P <: I1, I2 {
          | public:
          |   def this {}
          |   def m1(): String = "p1"
          |   def m2(): String = "p2"
          | }
          | class Q <: I1, I2 {
          | public:
          |   def this {}
          |   def m1(): String = "q1"
          |   def m2(): String = "q2"
          | }
          | static def main(args: String[]): String {
          |   val a = if true { new P() } else { new Q() }
          |   return a.m1()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
