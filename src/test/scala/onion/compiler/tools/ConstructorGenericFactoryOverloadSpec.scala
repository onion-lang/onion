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

  private def pair(first: String, second: String): String =
    s"""import { java.util.* }
       |class F[T] {
       |public:
       |  def this(xs: $first) { IO::println("F $first") }
       |  def this(xs: $second) { IO::println("F $second") }
       |}
       |def none[X](): X? = null
       |""".stripMargin

  describe("two candidates that both accept the retyped argument") {
    // Neither `Set[T]?` nor `List[T]?` is more specific: Java calls this ambiguous, and the
    // declaration order must not pick one silently.
    it("is still an error when they are incomparable, whichever is declared first") {
      for ((a, b) <- Seq(("Set[T]?", "List[T]?"), ("List[T]?", "Set[T]?"))) {
        val (result, out) = run(pair(a, b) + "val f = new F[String](none())\n")
        assert(result == Shell.Failure(-1), s"$a / $b: $out")
        assert(out.contains("E0021") || out.contains("E0022"), s"$a / $b: $out")
      }
    }

    it("takes the most specific one when they are comparable, whichever is declared first") {
      for ((a, b) <- Seq(("Collection[T]", "List[T]"), ("List[T]", "Collection[T]"))) {
        val (result, out) = run(pair(a, b) + "new F[String](Collections::emptyList())\n")
        assert(result != Shell.Failure(-1), s"$a / $b: $out")
        assert(out.trim == "F List[T]", s"$a / $b: $out")
      }
    }
  }

  describe("a varargs or default-parameter constructor that already resolves the call") {
    // `main` calls the fallback constructor here; retyping must not change it silently.
    it("keeps the varargs constructor for a generic factory argument") {
      val src =
        """import { java.util.* }
          |class D {
          |public:
          |  def this(xs: List[String]) { IO::println("list") }
          |  def this(o: Object...) { IO::println("varargs " + o.length) }
          |}
          |new D(Collections::emptyList())
          |new D(List::of())
          |""".stripMargin
      val (result, out) = run(src)
      assert(result != Shell.Failure(-1), out)
      assert(out.trim.linesIterator.toList == List("varargs 1", "varargs 1"), out)
    }

    it("keeps the default-parameter constructor for a generic factory argument") {
      val src =
        """import { java.util.* }
          |class D {
          |public:
          |  def this(xs: List[String]) { IO::println("list") }
          |  def this(xs: Set[String]) { IO::println("set") }
          |  def this(o: Object, n: Int = 7) { IO::println("obj-default " + n) }
          |}
          |new D(Collections::emptyList())
          |""".stripMargin
      val (result, out) = run(src)
      assert(result != Shell.Failure(-1), out)
      assert(out.trim == "obj-default 7", out)
    }
  }
}
