package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the path/extension helpers added to `onion.Files`: ext (file
 * extension), stem (name without extension), and withExtension (replace the
 * extension). Pure string operations — no filesystem access.
 */
class FilesPathSpec extends AbstractShellSpec {

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Files }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "FilesPath.on", Array()))
  }

  describe("Files path helpers") {
    it("ext returns the extension, empty for none or dotfiles") {
      runStr(
        "return Files::ext(\"dir/report.txt\") + \"|\" + Files::ext(\"README\") + \"|\" + Files::ext(\".gitignore\")",
        Shell.Success("txt||"))
    }

    it("stem returns the name without directory or extension") {
      runStr(
        "return Files::stem(\"dir/report.txt\") + \"|\" + Files::stem(\"README\")",
        Shell.Success("report|README"))
    }

    it("withExtension replaces the extension (leading dot ignored)") {
      runStr(
        "return Files::withExtension(\"file.txt\", \"md\") + \"|\" + Files::withExtension(\"data.csv\", \".json\")",
        Shell.Success("file.md|data.json"))
    }
  }
}
