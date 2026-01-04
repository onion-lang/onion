package onion.compiler.tools

import onion.tools.Shell

class TypePatternSpec extends AbstractShellSpec {
  describe("Type pattern matching") {
    it("matches sealed interface subtypes with type patterns") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: String) <: Result;
          |record Error(message: String) <: Result;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Result = new Success("Hello");
          |    return select ok {
          |      case s is Success: s.value()
          |      case e is Error: e.message()
          |      else: "unknown"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello") == result)
    }

    it("handles multiple type patterns") {
      val result = shell.run(
        """
          |sealed interface Tree {}
          |record Leaf(value: Int) <: Tree;
          |record Node(left: Tree, right: Tree) <: Tree;
          |class Test {
          |public:
          |  static def sum(t: Tree): Int {
          |    return select t {
          |      case leaf is Leaf: leaf.value()
          |      case node is Node: sum(node.left()) + sum(node.right())
          |      else: 0
          |    };
          |  }
          |  static def main(args: String[]): String {
          |    val tree = new Node(new Leaf(1), new Node(new Leaf(2), new Leaf(3)));
          |    return "" + sum(tree);
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("6") == result)
    }

    it("falls through to else branch when no type matches") {
      val result = shell.run(
        """
          |sealed interface Shape {}
          |record Circle(radius: Int) <: Shape;
          |record Square(side: Int) <: Shape;
          |record Triangle(base: Int, height: Int) <: Shape;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: Shape = new Triangle(3, 4);
          |    return select s {
          |      case c is Circle: "circle"
          |      case sq is Square: "square"
          |      else: "other"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("other") == result)
    }

    it("compiles exhaustive pattern match without else") {
      val result = shell.run(
        """
          |sealed interface Option {}
          |record Some(value: String) <: Option;
          |record None() <: Option;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val opt: Option = new Some("Hello");
          |    return select opt {
          |      case s is Some: s.value()
          |      case n is None: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello") == result)
    }

    it("reports error for non-exhaustive pattern match on sealed type") {
      val result = shell.run(
        """
          |sealed interface Status {}
          |record Active() <: Status;
          |record Inactive() <: Status;
          |record Pending() <: Status;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s: Status = new Active();
          |    return select s {
          |      case a is Active: "active"
          |      case i is Inactive: "inactive"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(result.isInstanceOf[Shell.Failure])
    }
  }
}
