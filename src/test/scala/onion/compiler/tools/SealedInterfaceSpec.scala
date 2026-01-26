package onion.compiler.tools

import onion.tools.Shell

class SealedInterfaceSpec extends AbstractShellSpec {
  describe("Sealed interface with record subtypes") {
    it("creates sealed interface with record implementations") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: String) <: Result;
          |record Error(code: Int) <: Result;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val ok: Result = new Success("Hello");
          |    val err: Result = new Error(404);
          |    return "ok";
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("allows method calls through interface type") {
      val result = shell.run(
        """
          |sealed interface Shape {}
          |record Circle(radius: Int) <: Shape;
          |record Rectangle(width: Int, height: Int) <: Shape;
          |class Test {
          |public:
          |  static def process(s: Shape): String {
          |    return s.toString();
          |  }
          |  static def main(args: String[]): String {
          |    val c = new Circle(10);
          |    val r = new Rectangle(5, 3);
          |    process(c);
          |    process(r);
          |    return "ok";
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("supports multiple interface implementations") {
      val result = shell.run(
        """
          |sealed interface Message {}
          |sealed interface Notification {}
          |record Alert(text: String) <: Message, Notification;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val a = new Alert("Warning!");
          |    val m: Message = a;
          |    val n: Notification = a;
          |    return "ok";
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("accesses record getters through cast") {
      val result = shell.run(
        """
          |sealed interface Result {}
          |record Success(value: String) <: Result;
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val r: Result = new Success("Hello");
          |    val s = r as Success;
          |    return s.value();
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("Hello") == result)
    }
  }
}
