package onion.compiler.tools

import onion.tools.Shell

class DefaultStaticImportSpec extends AbstractShellSpec {
  describe("Default static imports") {
    it("allows IO static calls without qualification") {
      val result = shell.run(
        """
          |class StaticImportSample {
          |public:
          |  static def main(args: String[]): String {
          |    println("hello")
          |    print("world")
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("resolves a varargs IO method (format) without qualification") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String = format("a=%s b=%d", "hi", 42)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("a=hi b=42") == result)
    }

    it("calls bare printf with a primitive vararg") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    printf("n=%d", 7)
          |    return "ok"
          |  }
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success("ok") == result)
    }

    it("calls bare Math methods (sqrt, max) without qualification") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Double = sqrt(max(9, 16) as Double)
          |}
          |""".stripMargin,
        "None",
        Array()
      )
      assert(Shell.Success(4.0) == result)
    }
  
  describe("the narrowed default set (#360): an effectful line looks effectful") {
    it("no longer resolves bare calls into Files, Http, DateTime or System") {
      for (call <- Seq(
        """readText("x.txt")""",       // onion.Files
        """get("https://x.example")""",// onion.Http
        """now()""",                   // onion.DateTime
        """getenv("HOME")"""           // java.lang.System
      )) {
        val r = shell.run(
          s"""class Test {
             |public:
             |  static def main(args: String[]): String {
             |    $call
             |    return "ok"
             |  }
             |}
             |""".stripMargin, "None", Array())
        assert(r.isInstanceOf[Shell.Failure], s"bare `$call` should not resolve: $r")
      }
    }

    it("still resolves the same members when called qualified, with no import") {
      val r = shell.run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val t = DateTime::format(DateTime::of(2026, 7, 26, 0, 0, 0), "yyyy")
          |    return t
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("2026") == r, r.toString)
    }

    it("restores bare calls via the explicit prelude import Class::*") {
      val r = shell.run(
        """import { onion.DateTime::*; }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return format(of(2026, 7, 26, 0, 0, 0), "yyyy")
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("2026") == r, r.toString)
    }

    it("imports a single static member via Class::member") {
      val r = shell.run(
        """import { java.lang.Integer::parseInt; }
          |class Test {
          |public:
          |  static def main(args: String[]): Int = parseInt("42")
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r, r.toString)
    }

    it("keeps the console exception: bare println still works") {
      val r = shell.run(
        """class Test {
          |public:
          |  static def main(args: String[]): String {
          |    println("still here")
          |    return "ok"
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("ok") == r, r.toString)
    }
  }
}
}
