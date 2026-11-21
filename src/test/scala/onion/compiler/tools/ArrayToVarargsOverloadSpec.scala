package onion.compiler.tools

import onion.tools.Shell

/**
 * #2955: an array handed to a `T...` slot is a fixed-arity call, as in Java. A static call with
 * `s(Object)` / `s(Object...)` takes the varargs overload for a `String[]`, and an `Int[]` is not
 * an `Object[]`, so an instance call takes `a(Object)`.
 */
class ArrayToVarargsOverloadSpec extends AbstractShellSpec {

  private val holder =
    """
      |import { java.util.List as JList; java.util.Set as JSet; java.util.stream.Stream }
      |class H {
      |public:
      |  static def s(o: Object): String = "s(Object)"
      |  static def s(xs: Object...): String = "s(Object...)"
      |  static def n(o: Object?): String = "n(Object?)"
      |  static def n(xs: Object...): String = "n(Object...)"
      |  def a(o: Object): String = "a(Object)"
      |  def a(xs: Object...): String = "a(Object...)"
      |}
      |""".stripMargin

  private def run(body: String, name: String) =
    shell.run(
      holder + "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n",
      name,
      Array()
    )

  describe("an array passed to an Object / Object... overload pair (#2955)") {
    it("a static call with a String[] takes the varargs overload") {
      val r = run("""    val arr: String[] = new String[2]
                    |    return H::s(arr)""".stripMargin, "StaticArray.on")
      assert(Shell.Success("s(Object...)") == r)
    }

    it("an instance call with an Int[] takes a(Object)") {
      val r = run("""    val ia: Int[] = new Int[2]
                    |    return new H().a(ia)""".stripMargin, "InstancePrimArray.on")
      assert(Shell.Success("a(Object)") == r)
    }

    it("an instance call with a String[] still takes the varargs overload") {
      val r = run("""    val arr: String[] = new String[2]
                    |    return new H().a(arr)""".stripMargin, "InstanceArray.on")
      assert(Shell.Success("a(Object...)") == r)
    }

    it("a single non-array argument still takes the fixed-arity overload") {
      val r = run("""    return H::s("x") + "," + H::s("x", "y")""", "StaticSingle.on")
      assert(Shell.Success("s(Object),s(Object...)") == r)
    }

    it("the same generic call picks the same overload at every call site, as Java does") {
      // `of(E)` with E = String[] and `of(E...)` with E = String instantiate to the same types;
      // Java takes `of(E...)`. The choice used to depend on the candidates' order.
      val r = run("""    val sa: String[] = ["a", "b", "c"].toArray(new String[0])
                    |    val l = JList::of(sa)
                    |    return "" + JList::of(sa).size() + JList::of(sa).size() + JList::of(sa).size() + l.size() +
                    |      JSet::of(sa).size() + Stream::of(sa).count()""".stripMargin, "GenericRepeat.on")
      assert(Shell.Success("333333") == r)
    }

    it("a static call with null keeps the fixed-arity overload, as before") {
      val r = run("""    return H::n(null)""", "StaticNull.on")
      assert(Shell.Success("n(Object?)") == r)
    }
  }
}
