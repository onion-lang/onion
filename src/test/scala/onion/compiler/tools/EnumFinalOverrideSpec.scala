package onion.compiler.tools

import onion.tools.Shell

/**
 * A homogeneous enum (java.lang.Enum subclass) that declares a method named
 * `name()` or `ordinal()` should be rejected at compile time with E0039
 * (FINAL_METHOD_OVERRIDE), not compile silently and then crash the JVM with
 * IncompatibleClassChangeError when the class is loaded.
 *
 * Root cause: TypingDuplicationPass.processEnumDeclaration was only calling
 * checkErasureSignatureCollisions, not checkOverrideContracts, so the final
 * modifier on java.lang.Enum.name() / ordinal() was never consulted.
 */
class EnumFinalOverrideSpec extends AbstractShellSpec {

  describe("Homogeneous enum overriding final java.lang.Enum methods") {

    it("rejects a method named name() in a homogeneous enum (E0039)") {
      val result = shell.run(
        """
          |enum Dir {
          |  E, W
          |public:
          |  def name(): String = "custom"
          |}
          |IO::println(Dir::E.name())
          |""".stripMargin,
        "EnumNameOverride.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("rejects a method named ordinal() in a homogeneous enum (E0039)") {
      val result = shell.run(
        """
          |enum Color {
          |  RED, GREEN, BLUE
          |public:
          |  def ordinal(): Int = 42
          |}
          |IO::println(Color::RED.ordinal())
          |""".stripMargin,
        "EnumOrdinalOverride.on",
        Array()
      )
      assert(Shell.Failure(-1) == result)
    }

    it("allows methods that do NOT collide with final java.lang.Enum methods") {
      val result = shell.run(
        """
          |enum Status {
          |  OK, FAIL
          |public:
          |  def describe(): String = select this {
          |    case Status::OK:   "all good"
          |    case Status::FAIL: "not good"
          |  }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return Status::OK.describe() + "/" + Status::FAIL.describe()
          |  }
          |}
          |""".stripMargin,
        "EnumDescribeOk.on",
        Array()
      )
      assert(Shell.Success("all good/not good") == result)
    }

    it("allows override of non-final toString() in a homogeneous enum") {
      val result = shell.run(
        """
          |enum Suit {
          |  CLUBS, DIAMONDS
          |public:
          |  override def toString(): String = select this {
          |    case Suit::CLUBS:    "clubs"
          |    case Suit::DIAMONDS: "diamonds"
          |  }
          |}
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return Suit::CLUBS.toString()
          |  }
          |}
          |""".stripMargin,
        "EnumToStringOk.on",
        Array()
      )
      assert(Shell.Success("clubs") == result)
    }
  }
}
