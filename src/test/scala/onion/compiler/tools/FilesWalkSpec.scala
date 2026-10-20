package onion.compiler.tools

import onion.tools.Shell
import java.nio.file.{Files => JFiles}

/**
 * `Files::walk(dir)` -- every file under a directory, the name Python's `os.walk` and Java's
 * `Files.walk` give it -- was E0005; the recursive listing existed only as
 * `Files::glob(dir, "**")`, which also returns directories. `walk` returns the regular
 * files under `dir`, as `/`-separated paths relative to it, sorted.
 */
class FilesWalkSpec extends AbstractShellSpec {
  describe("Files::walk") {
    it("lists every regular file under a directory, relative and sorted, without directories") {
      val root = JFiles.createTempDirectory("walk")
      JFiles.createDirectories(root.resolve("a/deep"))
      JFiles.createDirectories(root.resolve("b"))
      JFiles.writeString(root.resolve("a/deep/1.txt"), "x")
      JFiles.writeString(root.resolve("b/2.log"), "y")
      JFiles.writeString(root.resolve("3.txt"), "z")
      val dir = root.toString.replace("\\", "/")
      val result = shell.run(
        s"""
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    return "" + Files::walk("$dir") + Files::walk("$dir").filter { p -> p.endsWith(".txt") }.size
           |  }
           |}
           |""".stripMargin,
        "None",
        Array())
      assert(result == Shell.Success("[3.txt, a/deep/1.txt, b/2.log]2"))
    }

    it("returns an empty list for a path that is not a directory") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + Files::walk("/no/such/dir/for/walk")
          |  }
          |}
          |""".stripMargin,
        "None",
        Array())
      assert(result == Shell.Success("[]"))
    }
  }
}
