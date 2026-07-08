package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #309: the least-upper-bound of two ARRAY branch
 * types is the array of their component LUB (not Object), so the merged value
 * keeps its array-ness. JVM array covariance makes `Dog[]`/`Cat[]` both
 * assignable to `Animal[]`, so the merge is codegen-safe: `xs.length` and
 * element member calls stay available after the branch merge.
 */
class ArrayBranchLubSpec extends AbstractShellSpec {
  describe("branch LUB of two array types is the array of the component LUB") {
    it("a) subclass arrays merge to the superclass array (length + element call)") {
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
          |   // xs is typed Animal[] (the array LUB); at runtime it is the then-branch
          |   // Dog[], so we store a Dog (a Cat would ArrayStoreException — plain JVM
          |   // array covariance, exactly as in Java). The element call resolves through
          |   // the Animal element type: proof the LUB is Animal[], not Object.
          |   val xs = if true { new Dog[2] } else { new Cat[2] }
          |   xs[0] = new Dog()
          |   return "" + xs.length + ":" + xs[0].sound()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("2:woof") == result)
    }

    it("b) a select expression merging array branches keeps array-ness") {
      val result = shell.run(
        """
          | class Animal { public: def this {} }
          | class Dog : Animal { public: def this {} }
          | class Cat : Animal { public: def this {} }
          | static def main(args: String[]): Int {
          |   val n = 1
          |   val xs = select n {
          |     case 1: new Dog[3]
          |     case 2: new Cat[3]
          |     else: new Dog[1]
          |   }
          |   return xs.length
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }

    it("c) interface-sibling arrays merge to the shared-interface array") {
      val result = shell.run(
        """
          | interface Shape { def area(): Int }
          | class Sq <: Shape {
          | public:
          |   def this {}
          |   def area(): Int = 4
          | }
          | class Ci <: Shape {
          | public:
          |   def this {}
          |   def area(): Int = 3
          | }
          | static def main(args: String[]): Int {
          |   val xs = if true { new Sq[2] } else { new Ci[2] }
          |   xs[0] = new Sq()      // runtime array is Sq[]; store a matching element
          |   return xs.length + xs[0].area()
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(6) == result) // 2 (length) + 4 (Sq.area)
    }

    it("d) unrelated reference-element arrays still merge to an object array (length works)") {
      val result = shell.run(
        """
          | import { java.lang.* }
          | static def main(args: String[]): Int {
          |   val xs = if true { new String[3] } else { new JInteger[2] }
          |   return xs.length
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(3) == result)
    }
  }
}
