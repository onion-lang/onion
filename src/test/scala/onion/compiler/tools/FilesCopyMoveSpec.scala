package onion.compiler.tools

import onion.tools.Shell
import java.nio.file.{Files => JFiles}

/**
 * Tests for Files.copy/move/copyDir added to the stdlib.
 */
class FilesCopyMoveSpec extends AbstractShellSpec {
  describe("Files.copy") {
    it("copies a file and the copy exists") {
      val src = JFiles.createTempFile("onion-copy-src-", ".txt").toFile
      src.deleteOnExit()
      val dst = JFiles.createTempFile("onion-copy-dst-", ".txt").toFile
      dst.delete() // ensure dst doesn't exist beforehand
      JFiles.writeString(src.toPath, "copy content")

      val srcPath = src.getAbsolutePath
      val dstPath = dst.getAbsolutePath

      val result = shell.run(
        s"""
           |import { onion.Files; }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    Files::copy("$srcPath", "$dstPath")
           |    return Files::readText("$dstPath")
           |  }
           |}
           |""".stripMargin,
        "FilesCopy.on",
        Array()
      )
      assert(Shell.Success("copy content") == result)
    }

    it("copy replaces an existing destination") {
      val src = JFiles.createTempFile("onion-copy-src2-", ".txt").toFile
      src.deleteOnExit()
      val dst = JFiles.createTempFile("onion-copy-dst2-", ".txt").toFile
      dst.deleteOnExit()
      JFiles.writeString(src.toPath, "new content")
      JFiles.writeString(dst.toPath, "old content")

      val srcPath = src.getAbsolutePath
      val dstPath = dst.getAbsolutePath

      val result = shell.run(
        s"""
           |import { onion.Files; }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    Files::copy("$srcPath", "$dstPath")
           |    return Files::readText("$dstPath")
           |  }
           |}
           |""".stripMargin,
        "FilesCopyReplace.on",
        Array()
      )
      assert(Shell.Success("new content") == result)
    }
  }

  describe("Files.move") {
    it("moves a file: source disappears, destination appears") {
      val src = JFiles.createTempFile("onion-move-src-", ".txt").toFile
      // don't call deleteOnExit — it will be moved away
      val dst = JFiles.createTempFile("onion-move-dst-", ".txt").toFile
      dst.delete()
      JFiles.writeString(src.toPath, "move content")

      val srcPath = src.getAbsolutePath
      val dstPath = dst.getAbsolutePath

      val result = shell.run(
        s"""
           |import { onion.Files; }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    Files::move("$srcPath", "$dstPath")
           |    val srcGone: Boolean = !Files::exists("$srcPath")
           |    val dstOk: Boolean = Files::readText("$dstPath") == "move content"
           |    if srcGone && dstOk {
           |      return "ok"
           |    } else {
           |      return "fail"
           |    }
           |  }
           |}
           |""".stripMargin,
        "FilesMove.on",
        Array()
      )
      // Cleanup
      new java.io.File(dstPath).delete()
      assert(Shell.Success("ok") == result)
    }
  }

  describe("Files.copyDir") {
    it("recursively copies a directory tree") {
      val srcDir = JFiles.createTempDirectory("onion-copydir-src-").toFile
      val dstDir = JFiles.createTempDirectory("onion-copydir-dst-").toFile
      dstDir.delete() // remove so copyDir creates it

      val srcFile = new java.io.File(srcDir, "hello.txt")
      JFiles.writeString(srcFile.toPath, "dir content")

      val srcPath = srcDir.getAbsolutePath
      val dstPath = dstDir.getAbsolutePath

      val result = shell.run(
        s"""
           |import { onion.Files; }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    Files::copyDir("$srcPath", "$dstPath")
           |    return Files::readText("$dstPath/hello.txt")
           |  }
           |}
           |""".stripMargin,
        "FilesCopyDir.on",
        Array()
      )
      // Cleanup
      new java.io.File(dstPath + "/hello.txt").delete()
      dstDir.delete()
      srcFile.delete()
      srcDir.delete()
      assert(Shell.Success("dir content") == result)
    }
  }
}
