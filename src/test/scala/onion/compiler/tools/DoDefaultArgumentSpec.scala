package onion.compiler.tools

import onion.tools.Shell

/** Regression test for issue #3050: a `do[M]` block as a default argument is desugared. */
class DoDefaultArgumentSpec extends AbstractShellSpec {

  private val prelude = "import { onion.Option }\n"

  describe("a do[Option] default argument") {
    it("works on a top-level def") {
      val (r, out) = run(prelude +
        """def h(o: Option[Int] = do[Option] { x <- Option::some(6); yield x }): Option[Int] = o
          |IO::println("v=" + h().get())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("v=6"), out)
    }

    it("works on a method and a constructor") {
      val (r, out) = run(prelude +
        """class C(val o: Option[Int] = do[Option] { x <- Option::some(7); yield x }) {
          |public:
          |  def m(p: Option[Int] = do[Option] { x <- Option::some(8); yield x }): Int = p.get()
          |}
          |val c = new C()
          |IO::println("c=" + c.o.get() + " m=" + c.m())
          |""".stripMargin)
      assert(!r.isInstanceOf[Shell.Failure], out)
      assert(out.contains("c=7 m=8"), out)
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
