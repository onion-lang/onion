package onion.compiler.tools

import onion.tools.Shell

class DestructuringPatternSpec extends AbstractShellSpec {
  describe("Destructuring pattern matching") {
    it("destructures record with single field") {
      val result = shell.run(
        """
          |record Box(value: String);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val b = new Box("hello");
          |    return select b {
          |      case Box(v): v
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("hello") == result)
    }

    it("destructures record with multiple fields") {
      val result = shell.run(
        """
          |record Person(name: String, age: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Person("Alice", 30);
          |    return select p {
          |      case Person(n, a): n + ":" + a
          |      else: "none"
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Alice:30") == result)
    }

    it("destructures sealed interface subtypes") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: String) <: Result;
          |record Error(message: String) <: Result;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r: Result = new Success("ok");
          |    return select r {
          |      case Success(v): "got: " + v
          |      case Error(msg): "error: " + msg
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("got: ok") == result)
    }

    it("handles Error case in sealed interface") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: String) <: Result;
          |record Error(message: String) <: Result;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r: Result = new Error("oops");
          |    return select r {
          |      case Success(v): "got: " + v
          |      case Error(msg): "error: " + msg
          |    };
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("error: oops") == result)
    }

    it("reports error for wrong binding count") {
      val result = shell.run(
        """
          |record Point(x: Int, y: Int);
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val p = new Point(1, 2);
          |    return select p {
          |      case Point(a): "" + a
          |      else: "none"
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
