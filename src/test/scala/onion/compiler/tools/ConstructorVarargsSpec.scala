package onion.compiler.tools

import onion.tools.Shell

/**
 * #2839: a varargs constructor applies to spread arguments, as a varargs method does.
 * `new ProcessBuilder("echo", "x")` and `new V("a", "b")` for `def this(xs: String...)`
 * were E0021 even though the error listed the varargs constructor as available.
 */
class ConstructorVarargsSpec extends AbstractShellSpec {

  private def run(body: String, name: String) =
    shell.run(body.stripMargin, name, Array())

  describe("A varargs constructor") {
    it("takes spread arguments for a Java constructor (ProcessBuilder)") {
      val result = run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    // Run it rather than read `pb.command()`, which picks the wrong overload (#2859).
          |    val pb = new java.lang.ProcessBuilder("echo", "x")
          |    val out = pb.redirectErrorStream(true).start()
          |    val text = new String(out.getInputStream().readAllBytes()).trim()
          |    out.waitFor()
          |    return text
          |  }
          |}
          |""", "CtorVarargsJava.on")
      assert(Shell.Success("x") == result)
    }

    it("takes zero, one or many spread arguments for an Onion constructor") {
      val result = run(
        """
          |class V {
          |public:
          |  val n: Int
          |  def this(xs: String...) { this.n = xs.length }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new V().n + "," + new V("a").n + "," + new V("a", "b", "c").n
          |  }
          |}
          |""", "CtorVarargsOnion.on")
      assert(Shell.Success("0,1,3") == result)
    }

    it("still takes an array as the varargs parameter itself") {
      val result = run(
        """
          |class V {
          |public:
          |  val n: Int
          |  def this(xs: String...) { this.n = xs.length }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val arr = new String[4]
          |    return new V(arr).n
          |  }
          |}
          |""", "CtorVarargsArray.on")
      assert(Shell.Success(4) == result)
    }

    it("checks the fixed parameters and boxes primitive elements") {
      val result = run(
        """
          |class P {
          |public:
          |  val s: String
          |  def this(label: String, xs: Object...) {
          |    var acc = label
          |    foreach x in xs { acc = acc + "|" + x }
          |    this.s = acc
          |  }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    return new P("p", 1, "two", 3.5).s
          |  }
          |}
          |""", "CtorVarargsFixed.on")
      assert(Shell.Success("p|1|two|3.5") == result)
    }

    it("takes spread arguments for a generic class's T... parameter") {
      val result = run(
        """
          |class Bag[T] {
          |public:
          |  val n: Int
          |  def this(xs: T...) { this.n = xs.length }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b: Bag[String] = new Bag[String]("a", "b")
          |    return b.n
          |  }
          |}
          |""", "CtorVarargsGeneric.on")
      assert(Shell.Success(2) == result)
    }

    it("rejects an element of the wrong type (E0021)") {
      val result = run(
        """
          |class V {
          |public:
          |  val n: Int
          |  def this(xs: String...) { this.n = xs.length }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new V("a", 2).n
          |  }
          |}
          |""", "CtorVarargsWrong.on")
      assert(Shell.Failure(-1) == result)
    }

    it("is marked ACC_VARARGS so Java sees it as varargs") {
      val result = run(
        """
          |class V {
          |public:
          |  def this(xs: String...) { }
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Boolean {
          |    val c = Class::forName("V").getConstructors()[0]
          |    return c.isVarArgs()
          |  }
          |}
          |""", "CtorVarargsFlag.on")
      assert(Shell.Success(true) == result)
    }
  }
}
