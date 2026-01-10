package onion.compiler.tools

import onion.tools.Shell
import java.io.File
import java.nio.file.{Files => JFiles}

class FilesSpec extends AbstractShellSpec {
  describe("Files library") {
    describe("read and write text") {
      it("writes and reads text file") {
        val tmpFile = JFiles.createTempFile("onion-test-", ".txt").toFile
        tmpFile.deleteOnExit()
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Files::writeText("$path", "hello onion");
            |    return Files::readText("$path");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello onion") == result)
      }

      it("reads lines from file") {
        val tmpFile = JFiles.createTempFile("onion-test-", ".txt").toFile
        tmpFile.deleteOnExit()
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")
        JFiles.writeString(tmpFile.toPath, "line1\nline2\nline3")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val lines: String[] = Files::readLines("$path");
            |    return lines.length.toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("3") == result)
      }
    }

    describe("file operations") {
      it("checks if file exists") {
        val tmpFile = JFiles.createTempFile("onion-test-", ".txt").toFile
        tmpFile.deleteOnExit()
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Files::exists("$path")) {
            |      return "exists";
            |    } else {
            |      return "not found";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("exists") == result)
      }

      it("checks if path is file") {
        val tmpFile = JFiles.createTempFile("onion-test-", ".txt").toFile
        tmpFile.deleteOnExit()
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    if (Files::isFile("$path")) {
            |      return "is file";
            |    } else {
            |      return "not file";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("is file") == result)
      }

      it("deletes file") {
        val tmpFile = JFiles.createTempFile("onion-test-delete-", ".txt").toFile
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Files::delete("$path");
            |    if (Files::exists("$path")) {
            |      return "still exists";
            |    } else {
            |      return "deleted";
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("deleted") == result)
      }

      it("gets file size") {
        val tmpFile = JFiles.createTempFile("onion-test-", ".txt").toFile
        tmpFile.deleteOnExit()
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")
        JFiles.writeString(tmpFile.toPath, "12345")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Files::size("$path").toString();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("5") == result)
      }
    }

    describe("path operations") {
      it("joins paths") {
        val result = shell.run(
          """
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val parts: String[] = new String[]{"dir1", "dir2", "file.txt"};
            |    val joined: String = Files::joinPath(parts);
            |    // Check that it contains the file name
            |    if (joined.endsWith("file.txt")) {
            |      return "ok";
            |    } else {
            |      return joined;
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("ok") == result)
      }

      it("gets file name") {
        val result = shell.run(
          """
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return Files::getFileName("/path/to/file.txt");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("file.txt") == result)
      }

      it("gets parent path") {
        val result = shell.run(
          """
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val parent: String = Files::getParent("/path/to/file.txt");
            |    if (parent.endsWith("to")) {
            |      return "ok";
            |    } else {
            |      return parent;
            |    }
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("ok") == result)
      }
    }

    describe("append text") {
      it("appends to existing file") {
        val tmpFile = JFiles.createTempFile("onion-test-", ".txt").toFile
        tmpFile.deleteOnExit()
        val path = tmpFile.getAbsolutePath.replace("\\", "\\\\")
        JFiles.writeString(tmpFile.toPath, "hello")

        val result = shell.run(
          s"""
            |import { onion.Files; }
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    Files::appendText("$path", " world");
            |    return Files::readText("$path");
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        assert(Shell.Success("hello world") == result)
      }
    }
  }
}
