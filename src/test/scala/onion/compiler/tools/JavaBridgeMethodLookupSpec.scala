package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

// #2708: a Java class's synthetic bridge (`Integer.compareTo(Object)`, which
// javac emits for `Comparable[Integer]`) is not a method the source can call.
// Java resolves `i.compareTo("x")` against `compareTo(Integer)` only and rejects
// it; Onion used to pick the bridge, compile, and throw ClassCastException.
// A visibility bridge (`StringBuilder.length()`, inherited from a non-public
// superclass) has no real sibling and stays callable.
class JavaBridgeMethodLookupSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  private def run(body: String): Shell.Result =
    shell.run(
      s"""import { java.lang.Integer as JInteger }
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |${body.stripMargin}
         |  }
         |}
         |""".stripMargin, "JavaBridgeMethodLookup.on", Array())

  describe("A Java bridge method is not an overload (#2708)") {
    it("rejects JInteger.compareTo with a String argument (E0005)") {
      assert(errorCodes(
        """import { java.lang.Integer as JInteger }
          |val i: JInteger = 5
          |println(i.compareTo("x"))
          |""".stripMargin).contains("E0005"))
    }

    it("rejects StringBuilder.compareTo with a String argument (E0005)") {
      assert(errorCodes(
        """val sb = new StringBuilder("a")
          |println(sb.compareTo("b"))
          |""".stripMargin).contains("E0005"))
    }

    it("still calls JInteger.compareTo with a JInteger or an Int") {
      assert(run(
        """val i: JInteger = 5
          |val j: JInteger = 7
          |return "" + i.compareTo(j) + "," + i.compareTo(3)
          |""") == Shell.Success("-1,1"))
    }

    it("still calls compareTo through Comparable[String]") {
      assert(run(
        """val c: Comparable[String] = "q"
          |return "" + c.compareTo("r") + "," + "b".compareTo("a")
          |""") == Shell.Success("-1,1"))
    }

    it("keeps a visibility bridge callable (StringBuilder.length, charAt)") {
      assert(run(
        """val sb = new StringBuilder("abc")
          |sb.append("de").append(1)
          |return "" + sb.length() + sb.charAt(1) + sb.toString()
          |""") == Shell.Success("6babcde1"))
    }
  }
}
