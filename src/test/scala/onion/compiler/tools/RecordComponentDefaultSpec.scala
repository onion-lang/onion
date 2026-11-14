package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for issue #2829: a record component's default parsed, then every
 * construction that omitted it was E0021. The default now fills the component, as a
 * constructor default does.
 */
class RecordComponentDefaultSpec extends AbstractShellSpec {

  private def runScript(src: String, name: String): Shell.Result =
    shell.run(src.stripMargin, name, Array())

  describe("a record component default") {
    it("fills an omitted positional argument") {
      val result = runScript(
        """record R(a: Int, b: Int = 2)
          |class Test {
          |public:
          |  static def main(args: String[]): String = new R(1).toString() + " " + new R(1, 5).toString()
          |}
          |""", "RecordDefaultPositional.on")
      assert(result == Shell.Success("R(a=1, b=2) R(a=1, b=5)"))
    }

    it("fills an omitted argument around named ones, in any order") {
      val result = runScript(
        """record R(a: Int, b: Int = 2)
          |class Test {
          |public:
          |  static def main(args: String[]): String = new R(a = 1).toString() + " " + new R(b = 9, a = 3).toString()
          |}
          |""", "RecordDefaultNamed.on")
      assert(result == Shell.Success("R(a=1, b=2) R(a=3, b=9)"))
    }

    it("can read an earlier component, and is seen by a call written above the record") {
      val result = runScript(
        """class Test {
          |public:
          |  static def main(args: String[]): String = new Cfg().toString() + " " + new Cfg(port = 9090).toString()
          |}
          |record Cfg(host: String = "localhost", port: Int = host.length() * 1000)
          |""", "RecordDefaultEarlier.on")
      assert(result == Shell.Success("Cfg(host=localhost, port=9000) Cfg(host=localhost, port=9090)"))
    }

    it("works on a generic record and from the record's own static method") {
      val result = runScript(
        """record Box[T](v: T, label: String = "box") {
          |public:
          |  static def of(s: String): Box[String] = new Box[String](s)
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String = Box::of("x").label() + " " + new Box[Int](3).toString()
          |}
          |""", "RecordDefaultGeneric.on")
      assert(result == Shell.Success("box Box(v=3, label=box)"))
    }

    it("can read a top-level val declared before the record") {
      val (result, out) = captured(
        """val base = 7
          |record Q(a: Int, b: Int = base + a)
          |IO::println(new Q(1))
          |""".stripMargin)
      assert(result.isInstanceOf[Shell.Success], out)
      assert(out.contains("Q(a=1, b=8)"), out)
    }

    it("is checked against the component's type") {
      val (result, out) = captured(
        """record Bad(a: Int = "no")
          |IO::println(new Bad())
          |""".stripMargin)
      assert(result == Shell.Failure(-1), out)
      assert(out.contains("E0000"), out)
      assert(!out.contains("E0021"), out)
    }
  }

  private def captured(source: String): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array()) } }
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }
}
