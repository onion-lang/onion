package onion.compiler.tools

import onion.tools.Shell

/**
 * #2841: `c.f(Collections::emptyList())` with the overload pair `f(xs: List[T])` / `f(o: Object)`
 * picked `f(Object)`. Java infers the factory's element type from the slot and picks `f(List)`;
 * so does Onion now.
 */
class MethodGenericFactoryOverloadSpec extends AbstractShellSpec {
  private def run(source: String): (Shell.Result, String) = {
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

  private val classC =
    """import { java.util.* }
      |class C[T] {
      |public:
      |  def f(xs: List[T]): void { IO::println("list") }
      |  def f(o: Object): void { IO::println("obj") }
      |}
      |val c = new C[String]()
      |""".stripMargin

  describe("a generic factory call into a List[T] / Object method pair") {
    it("picks the List overload for Collections::emptyList()") {
      val (result, out) = run(classC + "c.f(Collections::emptyList())\n")
      assert(result != Shell.Failure(-1), out)
      assert(out.trim == "list", out)
    }

    it("still picks Object for an argument that is not a list") {
      val (result, out) = run(classC + "c.f(\"x\")\n")
      assert(result != Shell.Failure(-1), out)
      assert(out.trim == "obj", out)
    }
  }
}
