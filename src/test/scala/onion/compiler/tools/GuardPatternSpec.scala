package onion.compiler.tools

import onion.tools.Shell

class GuardPatternSpec extends AbstractShellSpec {
  describe("Guard pattern matching") {
    it("works with destructuring pattern and guard") {
      val result = shell.run(
        """
          |record Box(value: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box(10);
          |    return select b {
          |      case Box(v) when v > 5: "big: " + v
          |      case Box(v): "small: " + v
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("big: 10") == result)
    }

    it("small box falls to second case") {
      val result = shell.run(
        """
          |record Box(value: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box(3);
          |    return select b {
          |      case Box(v) when v > 5: "big: " + v
          |      case Box(v): "small: " + v
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("small: 3") == result)
    }

    it("guard with type pattern on sealed interface") {
      val result = shell.run(
        """
          |sealed interface Number {}
          |record Positive(value: Int) <: Number;
          |record Negative(value: Int) <: Number;
          |record Zero() <: Number;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Number = new Positive(5);
          |    return select n {
          |      case p is Positive when p.value() > 10: "big positive"
          |      case p is Positive: "positive"
          |      case n is Negative: "negative"
          |      case z is Zero: "zero"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("positive") == result)
    }

    it("guard matches first matching case") {
      val result = shell.run(
        """
          |sealed interface Number {}
          |record Positive(value: Int) <: Number;
          |record Negative(value: Int) <: Number;
          |record Zero() <: Number;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val n: Number = new Positive(15);
          |    return select n {
          |      case p is Positive when p.value() > 10: "big positive"
          |      case p is Positive: "positive"
          |      case n is Negative: "negative"
          |      case z is Zero: "zero"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("big positive") == result)
    }

    it("guard with simple expression pattern") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = "hello";
          |    return select s {
          |      case _ when s.length() > 3: "long"
          |      case _: "short"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("long") == result)
    }

    it("guard can reference outer variables") {
      val result = shell.run(
        """
          |record Box(value: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val threshold = 5;
          |    val b = new Box(3);
          |    return select b {
          |      case Box(v) when v > threshold: "above"
          |      case _: "below"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("below") == result)
    }
  }
}
