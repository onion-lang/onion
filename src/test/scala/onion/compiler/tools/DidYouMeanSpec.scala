package onion.compiler.tools

import onion.tools.Shell

class DidYouMeanSpec extends AbstractShellSpec {

  /** Runs `source`, capturing stdout+stderr, and asserts it's a compile failure
    * whose diagnostics contain every string in `mustContain`. Assertions are made
    * on error codes and on the suggested identifier itself (both locale-independent)
    * rather than on the localized "did you mean" wording around it.
    */
  private def failsWithSuggestion(source: String, mustContain: String*): Unit = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (o, e) = (System.out, System.err)
    val r =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array()) } }
      } finally { System.setOut(o); System.setErr(e) }
    val out = new String(buf.toByteArray, "UTF-8")
    assert(r.isInstanceOf[Shell.Failure], s"expected a compile failure, got $r\n$out")
    for (s <- mustContain) assert(out.contains(s), s"expected '$s' in diagnostics, got:\n$out")
  }

  describe("Error messages with suggestions") {
    describe("for method names") {
      it("reports error for typo in method name") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return "hello".tUpperCase();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // Method not found error should be reported
        // "did you mean: toUpperCase" suggestion will be shown in stderr
        assert(result.isInstanceOf[Shell.Failure])
      }

      it("reports error and a suggestion for a method name with wrong case") {
        // A pure case mismatch is the most unambiguous typo there is: the
        // lowercased name always matches exactly one real member, so the
        // suggestion must be there, not just the failure.
        failsWithSuggestion(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    return "hello".Length();
            |  }
            |}
            |""".stripMargin,
          "E0005", "length"
        )
      }
    }

    describe("for field/getter names") {
      it("reports error for typo in record field name") {
        val result = shell.run(
          """
            |record Person(name: String, age: Int);
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val p = new Person("Alice", 30);
            |    return p.nme();
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // "did you mean: name" suggestion will be shown
        assert(result.isInstanceOf[Shell.Failure])
      }
    }

    describe("for variable names") {
      it("reports error for typo in variable name") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val userName = "Alice";
            |    return usrName;
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // "did you mean: userName" suggestion will be shown
        assert(result.isInstanceOf[Shell.Failure])
      }

      it("reports error and a suggestion for a variable name with wrong case") {
        failsWithSuggestion(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val userName = "Alice";
            |    return UserName;
            |  }
            |}
            |""".stripMargin,
          "E0002", "userName"
        )
      }
    }

    describe("for class names") {
      it("reports error for typo in class name") {
        val result = shell.run(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    val list = new ArrayLst[String]();
            |    return "ok";
            |  }
            |}
            |""".stripMargin,
          "None",
          Array()
        )
        // Class not found error will be shown
        // "did you mean: ArrayList" suggestion may be shown if in import scope
        assert(result.isInstanceOf[Shell.Failure])
      }

      it("reports error and a suggestion for a local class name with wrong case") {
        failsWithSuggestion(
          """
            |class MyClass {
            |public:
            |  def this {}
            |}
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var x: myclass = new MyClass();
            |    return "ok";
            |  }
            |}
            |""".stripMargin,
          "E0003", "MyClass"
        )
      }
    }

    describe("for primitive type names") {
      it("reports error and a suggestion for a Java/Scala-style lowercase primitive name") {
        failsWithSuggestion(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var x: int = 0;
            |    return "ok";
            |  }
            |}
            |""".stripMargin,
          "E0003", "Int"
        )
      }

      it("suggests Boolean for the lowercase spelling boolean") {
        failsWithSuggestion(
          """
            |class Test {
            |public:
            |  static def main(args: String[]): String {
            |    var x: boolean = true;
            |    return "ok";
            |  }
            |}
            |""".stripMargin,
          "E0003", "Boolean"
        )
      }
    }

    describe("for named-argument names") {
      // The typo ("kount") shares no substring with the real name ("count"),
      // so matching on "count" in the output can only be the suggestion --
      // never an echo of the misspelled argument from the base message.
      it("suggests the right parameter name on a static call") {
        failsWithSuggestion(
          """
            |class T {
            |public:
            |  static def f(x: Int, count: Int): Int = x + count
            |  static def main(args: String[]): Int { return f(x = 1, kount = 2) }
            |}
            |""".stripMargin,
          "E0043", "count"
        )
      }

      it("suggests the right parameter name on an instance call") {
        failsWithSuggestion(
          """
            |class T {
            |public:
            |  def this {}
            |  def f(x: Int, count: Int): Int = x + count
            |}
            |class Test {
            |public:
            |  static def main(args: String[]): Int { return new T().f(x = 1, kount = 2) }
            |}
            |""".stripMargin,
          "E0043", "count"
        )
      }

      it("suggests the right parameter name on an unqualified call") {
        failsWithSuggestion(
          """
            |class T {
            |public:
            |  def this {}
            |  def f(x: Int, count: Int): Int = x + count
            |  def g(): Int = f(x = 1, kount = 2)
            |}
            |class Test {
            |public:
            |  static def main(args: String[]): Int { return new T().g() }
            |}
            |""".stripMargin,
          "E0043", "count"
        )
      }

      it("suggests the right parameter name on a constructor call") {
        failsWithSuggestion(
          """
            |class P {
            |  val x: Int
            |  val count: Int
            |public:
            |  def this(x: Int, count: Int) { self.x = x; self.count = count }
            |}
            |class Test {
            |public:
            |  static def main(args: String[]): Int { val p = new P(x = 1, kount = 2); return 0 }
            |}
            |""".stripMargin,
          "E0043", "count"
        )
      }
    }
  }
}
