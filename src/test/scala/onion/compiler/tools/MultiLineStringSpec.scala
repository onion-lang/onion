package onion.compiler.tools

import onion.tools.Shell

class MultiLineStringSpec extends AbstractShellSpec {
  describe("multi-line string literals") {
    it("should preserve newlines") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = $triple$Line1
          |Line2
          |Line3$triple$;
          |    return s;
          |  }
          |}
        """.stripMargin.replace("$triple$", "\"\"\""),
        "None",
        Array()
      )
      assert(Shell.Success("Line1\nLine2\nLine3") == result)
    }

    it("should preserve indentation") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = $triple$  indented
          |    more indent$triple$;
          |    return s;
          |  }
          |}
        """.stripMargin.replace("$triple$", "\"\"\""),
        "None",
        Array()
      )
      assert(Shell.Success("  indented\n    more indent") == result)
    }

    it("should support interpolation in multi-line strings") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val x = 42;
          |    val s = $triple$Value is: #{x}
          |Done$triple$;
          |    return s;
          |  }
          |}
        """.stripMargin.replace("$triple$", "\"\"\""),
        "None",
        Array()
      )
      assert(Shell.Success("Value is: 42\nDone") == result)
    }

    it("should allow single and double quotes inside") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = $triple$He said "Hello" and she said 'Hi'$triple$;
          |    return s;
          |  }
          |}
        """.stripMargin.replace("$triple$", "\"\"\""),
        "None",
        Array()
      )
      assert(Shell.Success("He said \"Hello\" and she said 'Hi'") == result)
    }

    it("should work with SQL-like content") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val table = "users";
          |    val sql = $triple$SELECT *
          |FROM #{table}
          |WHERE id = 1$triple$;
          |    return sql;
          |  }
          |}
        """.stripMargin.replace("$triple$", "\"\"\""),
        "None",
        Array()
      )
      assert(Shell.Success("SELECT *\nFROM users\nWHERE id = 1") == result)
    }

    it("should normalize CRLF line endings to LF") {
      // Built with explicit "\r\n" escapes (not physical line breaks), so the check does
      // not depend on this spec file's own checked-out line endings: a multi-line string
      // literal containing a literal CRLF -- as a source file authored on Windows would
      // have -- must still produce the same value a Unix-authored file would.
      val src = "class Test {\r\npublic:\r\n  static def main(args: String[]): String {\r\n    val s = \"\"\"Line1\r\nLine2\r\nLine3\"\"\";\r\n    return s;\r\n  }\r\n}\r\n"
      val result = shell.run(src, "None", Array())
      assert(Shell.Success("Line1\nLine2\nLine3") == result)
    }

    it("should normalize bare CR line endings to LF") {
      val src = "class Test {\rpublic:\r  static def main(args: String[]): String {\r    val s = \"\"\"Line1\rLine2\rLine3\"\"\";\r    return s;\r  }\r}\r"
      val result = shell.run(src, "None", Array())
      assert(Shell.Success("Line1\nLine2\nLine3") == result)
    }

    it("should normalize CRLF line endings inside an interpolated multi-line string") {
      val src = "class Test {\r\npublic:\r\n  static def main(args: String[]): String {\r\n    val x = 42;\r\n    val s = \"\"\"Value is: #{x}\r\nDone\"\"\";\r\n    return s;\r\n  }\r\n}\r\n"
      val result = shell.run(src, "None", Array())
      assert(Shell.Success("Value is: 42\nDone") == result)
    }

    it("should handle empty multi-line string") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val s = $triple$$triple$;
          |    return "empty:" + s + ":end";
          |  }
          |}
        """.stripMargin.replace("$triple$", "\"\"\""),
        "None",
        Array()
      )
      assert(Shell.Success("empty::end") == result)
    }
  }
}
