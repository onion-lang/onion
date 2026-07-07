package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression tests for issue #308: when one branch of an `if`/`select` is a
 * primitive and the other is a reference, the least-upper-bound must box the
 * primitive to its wrapper class and take the LUB of the two reference types
 * (bottoming out at Object), rather than erroring. This mirrors Java's
 * conditional-operator behaviour. The numeric+numeric promotion path (Int|Long /
 * Int|Double widening) and the #307 reference-sibling path must be preserved
 * unchanged.
 */
class BranchLubPrimitiveReferenceSpec extends AbstractShellSpec {
  describe("branch LUB of a primitive and a reference boxes to a common Object") {
    it("a) if with an Int branch and a String branch merges to Object") {
      val result = shell.run(
        """
          | class Sample {
          | public:
          |   static def main(args: String[]): String = {
          |     val x: Object = if true { 1 } else { "s" }
          |     val y: Object = if false { 1 } else { "s" }
          |     "#{x}|#{y}"
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1|s") == result)
    }

    it("b1) Int|Long widening LUB stays Long (NOT boxed to Object)") {
      // If the merge collapsed to Object, `x + 100L` would fail to resolve the
      // numeric operator; a green run proves the branch type stayed Long.
      val result = shell.run(
        """
          | class Sample {
          | public:
          |   static def main(args: String[]): String = {
          |     val x = if true { 1 } else { 2L }
          |     Long::toString(x + 100L)
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("101") == result)
    }

    it("b2) Int|Double widening LUB stays Double (NOT boxed to Object)") {
      val result = shell.run(
        """
          | class Sample {
          | public:
          |   static def main(args: String[]): String = {
          |     val x = if false { 1 } else { 2.0 }
          |     Double::toString(x + 0.5)
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("2.5") == result)
    }

    it("c) reference siblings (#307) still merge to their common ancestor") {
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
          | class Sample {
          | public:
          |   static def main(args: String[]): String = {
          |     val a = if true { new Dog() } else { new Cat() }
          |     a.sound()
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("woof") == result)
    }

    it("d) if with two Int branches still merges to Int") {
      val result = shell.run(
        """
          | class Sample {
          | public:
          |   static def main(args: String[]): String = {
          |     val x = if true { 1 } else { 2 }
          |     Int::toString(x + 10)
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("11") == result)
    }

    it("e) a select with a primitive and a reference branch merges to Object") {
      val result = shell.run(
        """
          | class Sample {
          | public:
          |   static def pick(n: Int): Object = select n {
          |     case 1: 42
          |     else: "other"
          |   }
          |   static def main(args: String[]): String = {
          |     "#{pick(1)}|#{pick(9)}"
          |   }
          | }
        """.stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("42|other") == result)
    }
  }
}
