package onion.compiler.tools

import onion.tools.Shell

/**
 * #2841: `new C[String](Collections::emptyList())` with the overload pair `this(xs: List[T])` /
 * `this(o: Object)` was E0021 (`List[Object]` found). Java infers the factory's element type from
 * the slot and picks the `List` constructor; so does Onion now.
 */
class ConstructorGenericFactoryOverloadSpec extends AbstractShellSpec {
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
      |  def this(xs: List[T]) { IO::println("list") }
      |  def this(o: Object) { IO::println("obj") }
      |}
      |""".stripMargin

  describe("a generic factory call into a List[T] / Object constructor pair") {
    it("picks the List constructor for Collections::emptyList()") {
      val (result, out) = run(classC + "new C[String](Collections::emptyList())\n")
      assert(result != Shell.Failure(-1), out)
      assert(out.trim == "list", out)
    }

    it("still picks Object for an argument that is not a list") {
      val (result, out) = run(classC + "new C[String](\"x\")\n")
      assert(result != Shell.Failure(-1), out)
      assert(out.trim == "obj", out)
    }
  }
}
