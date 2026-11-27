package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** A list or map literal takes its element types from a nullable target too (#3001):
  * `List[Int?]? = [1]` compiles, and `List[Int]? = [1, null]` is E0000 as for `List[Int]`. */
class NullableCollectionLiteralTargetSpec extends AbstractShellSpec {
  private def outcome(src: String): CompilationOutcome = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on")))
  }

  private def errorCodes(src: String): Seq[String] = outcome(src) match {
    case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
    case _ => Seq.empty
  }

  private def compiles(src: String): Boolean = outcome(src).isInstanceOf[CompilationOutcome.Success]

  describe("a literal for a nullable collection target") {
    it("List[Int?]? = [1] compiles") {
      assert(compiles("val a: List[Int?]? = [1]\nprintln(a)\n"))
    }
    it("List[Int?]? = [1, null] compiles") {
      assert(compiles("val a: List[Int?]? = [1, null]\nprintln(a)\n"))
    }
    it("Map[String, Int?]? = [\"a\": null] compiles") {
      assert(compiles("val m: Map[String, Int?]? = [\"a\": null]\nprintln(m)\n"))
    }
    it("List[Int]? = [1, null] is E0000, as for List[Int]") {
      assert(errorCodes("val b: List[Int]? = [1, null]\nprintln(b)\n") == Seq("E0000"))
    }
    it("Map[String, Int]? = [\"a\": null] is E0000") {
      assert(errorCodes("val m: Map[String, Int]? = [\"a\": null]\nprintln(m)\n") == Seq("E0000"))
    }
    it("List[String]? = [\"a\"] still compiles") {
      assert(compiles("val c: List[String]? = [\"a\"]\nprintln(c)\n"))
    }
    it("runs and prints the elements") {
      assert(shell.run("val a: List[Int?]? = [1, null]\nprintln(a)\n", "None", Array()).isInstanceOf[Shell.Success])
    }
  }
}
