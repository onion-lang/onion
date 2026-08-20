package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * break/continue with a label that isn't bound to any enclosing labeled loop
 * must report E0058 (LABEL_NOT_FOUND), not just fail generically.
 */
class LabelNotFoundSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("undefined label diagnostics") {
    it("reports E0058 for break with an unbound label") {
      assert(errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    foreach i: Int in 0..3 {
          |      break nosuch
          |    }
          |  }
          |}
          |""".stripMargin
      ).contains("E0058"))
    }

    it("reports E0058 for continue with an unbound label") {
      assert(errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    foreach i: Int in 0..3 {
          |      continue nosuch
          |    }
          |  }
          |}
          |""".stripMargin
      ).contains("E0058"))
    }

    it("does not report E0058 when the label is correctly bound") {
      assert(!errorCodes(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): void {
          |    outer: foreach i: Int in 0..3 {
          |      break outer
          |    }
          |  }
          |}
          |""".stripMargin
      ).contains("E0058"))
    }

    it("suggests the enclosing label's name for a near-miss typo") {
      // Assert on the error code and the suggested identifier only (both
      // locale-independent) rather than the localized "did you mean" wording.
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val (o, e) = (System.out, System.err)
      val r =
        try {
          System.setOut(ps); System.setErr(ps)
          Console.withOut(ps) { Console.withErr(ps) {
            shell.run(
              """
                |class Test {
                |public:
                |  static def main(args: String[]): void {
                |    outer: foreach i: Int in 0..3 {
                |      break outr
                |    }
                |  }
                |}
                |""".stripMargin,
              "None",
              Array()
            )
          } }
        } finally { System.setOut(o); System.setErr(e) }
      val out = new String(buf.toByteArray, "UTF-8")
      assert(r.isInstanceOf[onion.tools.Shell.Failure], s"expected a compile failure, got $r\n$out")
      assert(out.contains("E0058"), s"expected E0058 in diagnostics, got:\n$out")
      assert(out.contains("outer"), s"expected suggestion 'outer' in diagnostics, got:\n$out")
    }
  }
}
