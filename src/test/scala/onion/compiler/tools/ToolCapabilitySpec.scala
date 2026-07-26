package onion.compiler.tools

import onion.tools.Shell

/**
 * The capability boundary (issue #357): a `tool` cannot quietly do more than it says.
 *
 * Effects propagate through ordinary functions by inference with no annotation burden,
 * and are declared and checked only at `tool` boundaries. `unknown` is a real effect:
 * a Java method the table cannot vouch for must be admitted explicitly.
 */
class ToolCapabilitySpec extends AbstractShellSpec {

  describe("an honest tool") {
    it("compiles and runs when the declaration covers the body") {
      val r = shell.run(
        """tool shout(msg: String): String
          |  requires { console }
          |{
          |  IO::println(msg)
          |  return msg.toUpperCase()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String { return shout("hi") }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success("HI") == r)
    }

    it("needs no requires clause when the body is pure") {
      val r = shell.run(
        """tool double(n: Int): Int { return n * 2 }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return double(21) }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }

    it("covers effects reached through ordinary functions, transitively") {
      val r = shell.run(
        """def helper(msg: String): void { IO::println(msg) }
          |tool speak(msg: String): Int
          |  requires { console }
          |{
          |  helper(msg)
          |  return 0
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return speak("ok") }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
  }

  describe("E0077: the body performs an undeclared effect") {
    it("rejects a tool declaring only read when it writes (the issue's demo)") {
      val (r, out) = runCapturing(
        """tool sneaky(src: String, dst: String): void
          |  requires { read(src) }
          |{
          |  Files::writeText(dst, Files::readText(src))
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
      assert(r.isInstanceOf[Shell.Failure], s"expected failure, got $r")
      assert(out.contains("E0077"), out)
      // The diagnostic names the capability, the callee, and the call site's line.
      assert(out.contains("write"), out)
      assert(out.contains("writeText"), out)
      assert(out.contains(":4:"), out)
    }

    it("catches an effect hidden behind an ordinary function") {
      val (r, out) = runCapturing(
        """def leak(): void { IO::println("boo") }
          |tool quiet(): Int { leak(); return 0 }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
      assert(r.isInstanceOf[Shell.Failure], s"expected failure, got $r")
      assert(out.contains("E0077") && out.contains("console"), out)
    }

    it("treats an unlisted Java call as unknown, which must be admitted explicitly") {
      val bad =
        """import { java.util.Random; }
          |tool roll(): Int { return new Random().nextInt() }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin
      val (r1, out1) = runCapturing(bad)
      assert(r1.isInstanceOf[Shell.Failure], s"expected failure, got $r1")
      assert(out1.contains("E0077") && out1.contains("unknown"), out1)

      val ok = bad.replace("tool roll(): Int {", "tool roll(): Int\n  requires { unknown }\n{")
      val r2 = shell.run(ok, "None", Array())
      assert(Shell.Success(0) == r2, s"unknown admitted explicitly should compile: $r2")
    }
  }

  describe("E0078: a declared capability the body cannot perform") {
    it("rejects overclaiming") {
      val (r, out) = runCapturing(
        """tool overclaim(src: String): String
          |  requires { read(src), net }
          |{
          |  return Files::readText(src)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
      assert(r.isInstanceOf[Shell.Failure], s"expected failure, got $r")
      assert(out.contains("E0078") && out.contains("net"), out)
    }
  }

  describe("E0079: invalid capabilities") {
    it("rejects a name outside the vocabulary") {
      val (r, out) = runCapturing(
        """tool t(): Int requires { teleport } { return 0 }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
      assert(r.isInstanceOf[Shell.Failure], s"expected failure, got $r")
      assert(out.contains("E0079") && out.contains("teleport"), out)
    }

    it("rejects a parameter argument on an ambient effect") {
      val (r, out) = runCapturing(
        """tool t(x: String): Int requires { console(x) } { IO::println(x); return 0 }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
      assert(r.isInstanceOf[Shell.Failure], s"expected failure, got $r")
      assert(out.contains("E0079"), out)
    }

    it("rejects an argument that names no parameter") {
      val (r, out) = runCapturing(
        """tool t(src: String): String requires { read(nope) } { return Files::readText(src) }
          |class Test {
          |public:
          |  static def main(args: String[]): Int { return 0 }
          |}
          |""".stripMargin)
      assert(r.isInstanceOf[Shell.Failure], s"expected failure, got $r")
      assert(out.contains("E0079") && out.contains("nope"), out)
    }
  }

  describe("`tool` and `requires` stay ordinary identifiers") {
    it("parses them as locals and method names") {
      val r = shell.run(
        """class Test {
          |public:
          |  static def requires(n: Int): Int = n + 1
          |  static def main(args: String[]): Int {
          |    val tool = 40
          |    return requires(tool) + 1
          |  }
          |}
          |""".stripMargin, "None", Array())
      assert(Shell.Success(42) == r)
    }
  }

  /** Runs a script capturing everything the compiler prints (diagnostics go to stderr). */
  private def runCapturing(script: String): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val result = Console.withErr(ps) { Console.withOut(ps) {
      val saved = System.err
      System.setErr(ps)
      try shell.run(script, "None", Array())
      finally System.setErr(saved)
    }}
    (result, new String(buf.toByteArray, "UTF-8"))
  }
}
