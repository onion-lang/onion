package onion.compiler.tools

import onion.tools.Shell
import java.io.OutputStream
import java.io.PrintStream

class CompilationFailureSpec extends AbstractShellSpec {
  describe("Shell compilation failure handling") {
    it("returns failure when the snippet has syntax errors") {
      val result = silenceErr {
        shell.run(
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
