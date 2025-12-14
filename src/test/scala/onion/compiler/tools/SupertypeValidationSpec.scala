package onion.compiler.tools

import java.io.OutputStream
import java.io.PrintStream
import onion.tools.Shell

class SupertypeValidationSpec extends AbstractShellSpec {
  describe("Supertype validation") {
    it("rejects primitive superclass types") {
      val result = silenceErr {
        shell.run(
          """
            |class Bad : int {
            |public:
            |  static def main(args: String[]): Int { return 0 }
            |}
            |""".stripMargin,
          "BadSuper.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }

    it("rejects primitive interface types") {
      val result = silenceErr {
        shell.run(
          """
            |class Bad <: int {
            |public:
            |  static def main(args: String[]): Int { return 0 }
            |}
            |""".stripMargin,
          "BadInterface.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }

    it("rejects primitive super interfaces") {
      val result = silenceErr {
        shell.run(
          """
            |interface I <: int {
            |  def f(): int
            |}
            |
            |class UseI {
            |public:
            |  static def main(args: String[]): Int { return 0 }
            |}
            |""".stripMargin,
          "BadInterface2.on",
          Array()
        )
      }
      assert(Shell.Failure(-1) == result)
    }
  }

  private def silenceErr[A](block: => A): A = {
    val original = System.err
    val silent = new PrintStream(OutputStream.nullOutputStream())
    try {
      System.setErr(silent)
      block
    } finally {
      System.setErr(original)
      silent.close()
    }
  }
}

