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

    it("matches 3-level nested destructuring pattern") {
      val result = shell.run(
        """
          |record Inner(value: Int);
          |record Middle(inner: Inner);
          |record Outer(middle: Middle);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = new Outer(new Middle(new Inner(42)));
          |    return select o {
          |      case Outer(Middle(Inner(v))): "value=" + v
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("value=42") == result)
    }

    it("matches 4-level nested destructuring pattern") {
      val result = shell.run(
        """
          |record Level4(value: Int);
          |record Level3(l4: Level4);
          |record Level2(l3: Level3);
          |record Level1(l2: Level2);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val obj = new Level1(new Level2(new Level3(new Level4(99))));
          |    return select obj {
          |      case Level1(Level2(Level3(Level4(v)))): "deep=" + v
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("deep=99") == result)
    }

    it("matches deep nested pattern with multiple bindings") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |record Segment(start: Point, end: Point);
          |record Path(seg: Segment);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Path(new Segment(new Point(1, 2), new Point(3, 4)));
          |    return select p {
          |      case Path(Segment(Point(x1, y1), Point(x2, y2))):
          |        "(" + x1 + "," + y1 + ")->(" + x2 + "," + y2 + ")"
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("(1,2)->(3,4)") == result)
    }

    it("matches deep nested pattern with wildcards at different levels") {
      val result = shell.run(
        """
          |record Inner(a: Int, b: Int);
          |record Middle(inner: Inner);
          |record Outer(middle: Middle);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val o = new Outer(new Middle(new Inner(10, 20)));
          |    return select o {
          |      case Outer(Middle(Inner(x, _))): "x=" + x
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("x=10") == result)
    }

    it("falls through when deep nested pattern does not match") {
      val result = shell.run(
        """
          |sealed interface Data {}
          |record Value(n: Int) <: Data;
          |record Pair(a: Data, b: Data) <: Data;
          |record Container(data: Data);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val c = new Container(new Pair(new Value(1), new Pair(new Value(2), new Value(3))));
          |    return select c {
          |      case Container(Pair(Value(x), Value(y))): "two values: " + x + "," + y
          |      case Container(Pair(Value(x), _)): "left value: " + x
          |      else: "complex"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("left value: 1") == result)
    }
  }
}
