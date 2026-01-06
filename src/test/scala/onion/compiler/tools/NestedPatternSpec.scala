package onion.compiler.tools

import onion.tools.Shell

class NestedPatternSpec extends AbstractShellSpec {
  describe("Nested pattern matching") {
    it("matches nested destructuring pattern") {
      val result = shell.run(
        """
          |sealed interface Tree {}
          |record Leaf(value: Int) <: Tree;
          |record Node(left: Tree, right: Tree) <: Tree;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val t: Tree = new Node(new Leaf(1), new Leaf(2));
          |    return select t {
          |      case Node(Leaf(x), Leaf(y)): "" + x + "," + y
          |      case _: "other"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("1,2") == result)
    }

    it("matches nested pattern with wildcard") {
      val result = shell.run(
        """
          |sealed interface Tree {}
          |record Leaf(value: Int) <: Tree;
          |record Node(left: Tree, right: Tree) <: Tree;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val t: Tree = new Node(new Leaf(5), new Node(new Leaf(3), new Leaf(4)));
          |    return select t {
          |      case Node(Leaf(x), _): "left leaf: " + x
          |      case _: "other"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("left leaf: 5") == result)
    }

    it("falls through when nested pattern does not match") {
      val result = shell.run(
        """
          |sealed interface Tree {}
          |record Leaf(value: Int) <: Tree;
          |record Node(left: Tree, right: Tree) <: Tree;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val t: Tree = new Node(new Node(new Leaf(1), new Leaf(2)), new Leaf(3));
          |    return select t {
          |      case Node(Leaf(x), _): "left is leaf: " + x
          |      case Node(_, Leaf(y)): "right is leaf: " + y
          |      case _: "other"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("right is leaf: 3") == result)
    }

    it("works with simple binding alongside nested pattern") {
      val result = shell.run(
        """
          |record Pair(first: Int, second: Int);
          |record Box(value: Pair);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box(new Pair(10, 20));
          |    return select b {
          |      case Box(Pair(a, b)): "" + a + "+" + b
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("10+20") == result)
    }
  }
}
