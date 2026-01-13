package onion.compiler.tools

import onion.tools.Shell

class CompilationFailureSpec extends AbstractShellSpec {
  describe("Shell compilation failure handling") {
    it("returns failure when the snippet has syntax errors") {
      val result = shell.run(
        """
          |class MissingBody {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0
          |
        """.stripMargin,
        "Broken.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when variable is not found") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    return undefinedVar;
          |  }
          |}
        """.stripMargin,
        "UndefinedVar.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when method is not found") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  def helper(): Int {
          |    return this.nonExistentMethod();
          |  }
          |  static def main(args: String[]): Int {
          |    return new Test1().helper();
          |  }
          |}
        """.stripMargin,
        "NoMethod.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when types are incompatible") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    var x: Int = "string";
          |    return x;
          |  }
          |}
        """.stripMargin,
        "TypeMismatch.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when trying to override final method") {
      val result = shell.run(
        """
          |class Base {
          |public:
          |  final def calculate(): Int { return 42; }
          |}
          |class Derived : Base {
          |public:
          |  def calculate(): Int { return 100; }
          |}
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int { return 0; }
          |}
        """.stripMargin,
        "FinalOverride.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when trying to instantiate abstract class") {
      val result = shell.run(
        """
          |abstract class Animal {
          |public:
          |  abstract def speak(): String;
          |}
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    val a: Animal = new Animal();
          |    return 0;
          |  }
          |}
        """.stripMargin,
        "AbstractInstantiation.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when abstract method is not implemented") {
      val result = shell.run(
        """
          |abstract class Animal {
          |public:
          |  abstract def speak(): String;
          |}
          |class Dog : Animal {
          |public:
          |  // Missing speak() implementation
          |}
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    return 0;
          |  }
          |}
        """.stripMargin,
        "MissingAbstractImpl.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when assigning to val") {
      val result = shell.run(
        """
          |class Test1 {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Int = 10;
          |    x = 20;
          |    return x;
          |  }
          |}
        """.stripMargin,
        "ValAssignment.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when break is used outside a loop") {
      val result = shell.run(
        """
          |class BreakOutside {
          |public:
          |  static def main(args: String[]): Int {
          |    break
          |    return 0
          |  }
          |}
        """.stripMargin,
        "BreakOutside.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when continue is used outside a loop") {
      val result = shell.run(
        """
          |class ContinueOutside {
          |public:
          |  static def main(args: String[]): Int {
          |    continue
          |    return 0
          |  }
          |}
        """.stripMargin,
        "ContinueOutside.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when this is used in a static context") {
      val result = shell.run(
        """
          |class StaticThis {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Object = this
          |    return 0
          |  }
          |}
        """.stripMargin,
        "StaticThis.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when self is used in a static context") {
      val result = shell.run(
        """
          |class StaticSelf {
          |public:
          |  static def main(args: String[]): Int {
          |    val x: Object = self
          |    return 0
          |  }
          |}
        """.stripMargin,
        "StaticSelf.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when this is used in a static field initializer") {
      val result = shell.run(
        """
          |class StaticFieldThis {
          |public:
          |  static val value: Object = this
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
        """.stripMargin,
        "StaticFieldThis.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("returns failure when self is used in a static field initializer") {
      val result = shell.run(
        """
          |class StaticFieldSelf {
          |public:
          |  static val value: Object = self
          |  static def main(args: String[]): Int {
          |    return 0
          |  }
          |}
        """.stripMargin,
        "StaticFieldSelf.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }
  }
}
