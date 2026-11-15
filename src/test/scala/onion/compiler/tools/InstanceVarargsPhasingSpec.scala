package onion.compiler.tools

import onion.tools.Shell

/**
 * #2859: an instance call takes a fixed-arity overload over a varargs one, as Java does.
 * Varargs are only considered when no fixed-arity overload applies, so `pb.command()` is the
 * zero-argument getter, not `command(String...)`.
 */
class InstanceVarargsPhasingSpec extends AbstractShellSpec {

  describe("instance calls and varargs overloads (#2859)") {
    it("picks ProcessBuilder.command() over command(String...) with no argument") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val l: List[String] = new ArrayList[String]()
          |    l.add("echo")
          |    val pb = new java.lang.ProcessBuilder(l)
          |    val c: List[String] = pb.command()
          |    return c.size() + new java.lang.ProcessBuilder("echo", "x").command().size() * 10
          |  }
          |}
          |""".stripMargin,
        "PbCommand.on",
        Array()
      )
      assert(Shell.Success(21) == result)
    }

    it("picks an Onion getter m() over m(xs: String...)") {
      val result = shell.run(
        """
          |class V {
          |public:
          |  def m(): String = "getter"
          |  def m(xs: String...): V = this
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new V
          |    val s: String = v.m()
          |    return s
          |  }
          |}
          |""".stripMargin,
        "GetterOverVarargs.on",
        Array()
      )
      assert(Shell.Success("getter") == result)
    }

    it("picks m(Object) over m(String...) for one argument, as Java does") {
      val result = shell.run(
        """
          |class V {
          |public:
          |  def m(o: Object): String = "object"
          |  def m(xs: String...): String = "varargs"
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new V().m("a")
          |  }
          |}
          |""".stripMargin,
        "ObjectOverVarargs.on",
        Array()
      )
      assert(Shell.Success("object") == result)
    }

    it("still calls the varargs overload when no fixed-arity one applies") {
      val result = shell.run(
        """
          |class V {
          |public:
          |  def m(): String = "getter"
          |  def m(xs: String...): String = "varargs" + xs.length
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val v = new V
          |    return v.m("a", "b") + "," + v.m("a")
          |  }
          |}
          |""".stripMargin,
        "VarargsStillWorks.on",
        Array()
      )
      assert(Shell.Success("varargs2,varargs1") == result)
    }
  }
}
