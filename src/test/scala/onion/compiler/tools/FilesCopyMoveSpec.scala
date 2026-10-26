package onion.compiler.tools

import onion.compiler.exceptions.ScriptException
import onion.tools.Shell
import java.nio.file.{Files => JFiles}

/**
 * Tests for Files.copy/move/copyDir added to the stdlib.
 *
 * Paths are spliced into Onion string literals with forward slashes: a Windows `\` would
 * start an escape there.
 */
class FilesCopyMoveSpec extends AbstractShellSpec {
  describe("Files.copy") {
    it("copies a file and the copy exists") {
      val src = JFiles.createTempFile("onion-copy-src-", ".txt").toFile
      src.deleteOnExit()
      val dst = JFiles.createTempFile("onion-copy-dst-", ".txt").toFile
      dst.delete() // ensure dst doesn't exist beforehand
      JFiles.writeString(src.toPath, "copy content")

      val srcPath = src.getAbsolutePath.replace('\\', '/')
      val dstPath = dst.getAbsolutePath.replace('\\', '/')

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

      val srcPath = src.getAbsolutePath.replace('\\', '/')
      val dstPath = dst.getAbsolutePath.replace('\\', '/')

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

      val srcPath = src.getAbsolutePath.replace('\\', '/')
      val dstPath = dst.getAbsolutePath.replace('\\', '/')

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

      val srcPath = srcDir.getAbsolutePath.replace('\\', '/')
      val dstPath = dstDir.getAbsolutePath.replace('\\', '/')

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

    it("refuses to copy a directory into its own subdirectory, up front, instead of recursing until the OS path limit") {
      val srcDir = JFiles.createTempDirectory("onion-copydir-self-").toFile
      srcDir.deleteOnExit()
      val srcFile = new java.io.File(srcDir, "a.txt")
      JFiles.writeString(srcFile.toPath, "1")

      val srcPath = srcDir.getAbsolutePath.replace('\\', '/')
      val dstPath = srcPath + "/sub"

      val thrown = intercept[ScriptException] {
        shell.run(
          s"""
             |import { onion.Files; }
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    Files::copyDir("$srcPath", "$dstPath")
             |    return "no error"
             |  }
             |}
             |""".stripMargin,
          "FilesCopyDirIntoSubdir.on",
          Array()
        )
      }
      assert(thrown.getCause.isInstanceOf[IllegalArgumentException])
      assert(!new java.io.File(dstPath).exists())

      // Cleanup
      srcFile.delete()
      srcDir.delete()
    }

    it("refuses to copy a directory into itself (same path)") {
      val srcDir = JFiles.createTempDirectory("onion-copydir-same-").toFile
      srcDir.deleteOnExit()
      val srcFile = new java.io.File(srcDir, "a.txt")
      JFiles.writeString(srcFile.toPath, "1")

      val srcPath = srcDir.getAbsolutePath.replace('\\', '/')

      val thrown = intercept[ScriptException] {
        shell.run(
          s"""
             |import { onion.Files; }
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    Files::copyDir("$srcPath", "$srcPath")
             |    return "no error"
             |  }
             |}
             |""".stripMargin,
          "FilesCopyDirSamePath.on",
          Array()
        )
      }
      assert(thrown.getCause.isInstanceOf[IllegalArgumentException])

      // Cleanup
      srcFile.delete()
      srcDir.delete()
    }

    it("refuses to copy a directory into itself through a symlink") {
      val base = JFiles.createTempDirectory("onion-copydir-link-")
      val srcDir = JFiles.createDirectory(base.resolve("sub"))
      JFiles.writeString(srcDir.resolve("a.txt"), "1")
      JFiles.createSymbolicLink(base.resolve("lnk"), srcDir)

      val srcPath = srcDir.toString.replace('\\', '/')
      val dstPath = base.resolve("lnk").resolve("in").toString.replace('\\', '/')

      val thrown = intercept[ScriptException] {
        shell.run(
          s"""
             |import { onion.Files; }
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    Files::copyDir("$srcPath", "$dstPath")
             |    return "no error"
             |  }
             |}
             |""".stripMargin,
          "FilesCopyDirSymlink.on",
          Array()
        )
      }
      assert(thrown.getCause.isInstanceOf[IllegalArgumentException])
      assert(!JFiles.exists(srcDir.resolve("in")))
    }

    it("refuses a destination inside the source that reaches it through symlink/..") {
      val base = JFiles.createTempDirectory("onion-copydir-dotdot-in-")
      val srcDir = JFiles.createDirectories(base.resolve("sub"))
      JFiles.createDirectories(srcDir.resolve("deep"))
      JFiles.writeString(srcDir.resolve("a.txt"), "1")
      JFiles.createSymbolicLink(base.resolve("lnk"), base.resolve("sub").resolve("deep"))

      val srcPath = srcDir.toString.replace('\\', '/')
      // lnk/.. is sub (the OS follows lnk first), so this is sub/in: inside the source
      val dstPath = (base.resolve("lnk").toString + "/../in").replace('\\', '/')

      val thrown = intercept[ScriptException] {
        shell.run(
          s"""
             |import { onion.Files; }
             |class Test {
             |public:
             |  static def main(args: String[]): String {
             |    Files::copyDir("$srcPath", "$dstPath")
             |    return "no error"
             |  }
             |}
             |""".stripMargin,
          "FilesCopyDirDotDotInside.on",
          Array()
        )
      }
      assert(thrown.getCause.isInstanceOf[IllegalArgumentException])
      assert(!JFiles.exists(srcDir.resolve("in")))
    }

    it("copies when symlink/.. leads outside the source") {
      val base = JFiles.createTempDirectory("onion-copydir-dotdot-out-")
      val srcDir = JFiles.createDirectories(base.resolve("other"))
      JFiles.createDirectories(base.resolve("far").resolve("deep"))
      JFiles.writeString(srcDir.resolve("a.txt"), "1")
      JFiles.createSymbolicLink(base.resolve("lnk"), base.resolve("far").resolve("deep"))

      val srcPath = srcDir.toString.replace('\\', '/')
      // lnk/.. is far, so this is far/other/x: outside the source, a legal copy
      val dstPath = (base.resolve("lnk").toString + "/../other/x").replace('\\', '/')

      val result = shell.run(
        s"""
           |import { onion.Files; }
           |class Test {
           |public:
           |  static def main(args: String[]): String {
           |    Files::copyDir("$srcPath", "$dstPath")
           |    return "ok"
           |  }
           |}
           |""".stripMargin,
        "FilesCopyDirDotDotOutside.on",
        Array()
      )
      assert(result == Shell.Success("ok"))
      assert(JFiles.exists(base.resolve("far").resolve("other").resolve("x").resolve("a.txt")))
    }
  }
}
