package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{CompilerConfig, OnionCompiler, ScalarConversions, StreamInputSource}
import onion.compiler.toolbox.Message

import java.io.StringReader

/**
 * A trailing `List[String]` on an auto-CLI `main` collects the remaining
 * arguments like `String[]` does (#2576). A clean run is `Shell.Success(null)`;
 * a mismatch throws inside main.
 */
class ListMainAutoCliSpec extends AbstractShellSpec {
  private def prog(expectedCount: Int): String =
    s"""
       |def main(cmd: String, files: List[String]): void {
       |  if cmd != "build" { throw new RuntimeException("cmd=" + cmd) }
       |  if files.size != $expectedCount { throw new RuntimeException("n=" + files.size) }
       |  if $expectedCount == 2 && files[1] != "b.txt" { throw new RuntimeException("second=" + files[1]) }
       |}
       |""".stripMargin
  describe("List[String]-parameter main auto-CLI") {
    it("collects trailing arguments into the List rest") {
      assert(Shell.Success(null) == shell.run(prog(2), "None", Array("build", "a.txt", "b.txt")))
    }
    it("allows an empty rest") {
      assert(Shell.Success(null) == shell.run(prog(0), "None", Array("build")))
    }
    it("accepts a lone List[String] as the whole argument list") {
      val src =
        """
          |def main(files: List[String]): void {
          |  if files.size != 2 || files[0] != "a" || files[1] != "b" { throw new RuntimeException("files=" + files) }
          |}
          |""".stripMargin
      assert(Shell.Success(null) == shell.run(src, "None", Array("a", "b")))
      val empty = "def main(files: List[String]): void { if files.size != 0 { throw new RuntimeException(\"n\") } }"
      assert(Shell.Success(null) == shell.run(empty, "None", Array()))
    }
  }

  // A List[String] that is not a valid rest collector is reported like a String[] one:
  // E0095 (misplaced), never E0094 (unreadable type), whose message now names it as supported.
  describe("a List[String] main parameter in an unsupported position") {
    def codes(src: String): Seq[Option[String]] =
      new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10))
        .compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "Test.on")))
        .allErrors.map(_.code)

    it("is E0095 when it has a default") {
      assert(codes("def main(name: String, tags: List[String] = []): void { }") == Seq(Some("E0095")))
    }
    it("is E0095 after a defaulted scalar, as String[] is") {
      val list = codes("def main(n: Int, loud: Boolean = false, files: List[String]): void { }")
      val arr = codes("def main(n: Int, loud: Boolean = false, files: String[]): void { }")
      assert(list == Seq(Some("E0095")))
      assert(list == arr)
    }
    it("is E0095 when it is not last") {
      assert(codes("def main(files: List[String], n: Int): void { }") == Seq(Some("E0095")))
    }
    it("E0094 names List[String] among the supported types") {
      val msg = Message("error.semantic.mainParameterNotCliConvertible", Array[Any]("ids", "List[Int]", ScalarConversions.supportedNames))
      assert(msg.contains("List[String]"))
      assert(codes("def main(ids: List[Int]): void { }") == Seq(Some("E0094")))
    }
  }
}
