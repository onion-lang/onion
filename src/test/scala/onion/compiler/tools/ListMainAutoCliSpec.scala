package onion.compiler.tools

import onion.tools.Shell

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
    it("still rejects a defaulted List[String] with E0094") {
      val src = "def main(name: String, tags: List[String] = []): void { }"
      assert(Shell.Failure(-1) == shell.run(src, "None", Array("x")))
    }
  }
}
