package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression test for issue #3063: `do[M]` inside an `extension` method or a homogeneous
 * (data-carrying) enum was never desugared. The extension method failed with a VerifyError
 * at run time, and the enum constant was silently `null`.
 */
class DoInExtensionAndEnumSpec extends AbstractShellSpec {

  private val prelude = "import { onion.Option }\n"

  describe("do[Option] inside an extension method") {
    it("runs as its body") {
      val (r, out) = run(prelude +
        """extension String { def f(): Option[Int] = do[Option] { x <- Option::some(6); yield x } }
          |IO::println("v=" + "a".f().get())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("v=6"), out)
    }

    it("runs as a default argument that the call omits") {
      val (r, out) = run(prelude +
        """extension String {
          |  def g(o: Option[Int] = do[Option] { x <- Option::some(7); yield x }): Int = o.get() + this.length()
          |}
          |IO::println("g=" + "ab".g())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("g=9"), out)
    }
  }

  describe("do[Option] inside a data-carrying enum") {
    it("builds a constant whose argument is a do block") {
      val (r, out) = run(prelude +
        """enum P(o: Option[Int]) { A(Option::some(5)), B(do[Option] { x <- Option::some(5); yield x + 1 }) }
          |IO::println("a=" + P::A + " b=" + P::B + " bo=" + P::B.o().get())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("a=A b=B bo=6"), out)
    }

    it("desugars a do block in an enum method") {
      val (r, out) = run(prelude +
        """enum Q(n: Int) {
          |  X(1), Y(2)
          |public:
          |  def twice(): Option[Int] = do[Option] { v <- Option::some(n()); yield v * 2 }
          |}
          |IO::println("y=" + Q::Y.twice().get())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("y=4"), out)
    }
  }

  private def run(source: String): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val ps = new java.io.PrintStream(buf, true, "UTF-8")
    val (savedOut, savedErr) = (System.out, System.err)
    val result =
      try {
        System.setOut(ps); System.setErr(ps)
        Console.withOut(ps) { Console.withErr(ps) { shell.run(source, "None", Array.empty[String]) } }
      } finally { System.setOut(savedOut); System.setErr(savedErr) }
    (result, new String(buf.toByteArray, "UTF-8"))
  }
}
