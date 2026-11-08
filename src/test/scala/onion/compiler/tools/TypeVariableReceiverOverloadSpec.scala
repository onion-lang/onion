package onion.compiler.tools

import onion.tools.Shell

/**
 * #2555: with overloads `f(xs: List[T])` and `f(o: Object)` inside `class N[T extends Number]`,
 * a `List[Number]` argument fits only `f(Object)`, as in Java. When the receiver's type
 * arguments were type variables (`me.f(xs)` with `me: N[T]`, or a bare `f(xs)`), resolution
 * read `T` through its bound, chose `f(List[T])`, and then rejected the argument with E0000.
 */
class TypeVariableReceiverOverloadSpec extends AbstractShellSpec {
  private def program(body: String, main: String): String =
    s"""class N[T extends Number] {
       |public:
       |  def this {}
       |  def f(xs: List[T]): String = "list"
       |  def f(o: Object): String = "obj"
       |$body
       |}
       |class Test {
       |public:
       |  static def main(args: String[]): String {
       |    $main
       |  }
       |}
       |""".stripMargin

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

  describe("an overload with a List[T] slot, called through a type-variable receiver") {
    it("a List[Number] through `me: N[T]` takes f(Object)") {
      val src = program(
        """  def go(xs: List[Number]): String {
          |    val me: N[T] = this
          |    return me.f(xs)
          |  }""".stripMargin,
        "return new N[Integer]().go([1.5])")
      assert(run(src)._1 == Shell.Success("obj"))
    }

    it("a List[Number] through a bare self-call takes f(Object)") {
      val src = program(
        "  def go(xs: List[Number]): String = f(xs)",
        "return new N[Integer]().go([1.5])")
      assert(run(src)._1 == Shell.Success("obj"))
    }

    it("a List[T] still takes f(List[T]), through `me` and through a bare call") {
      val src = program(
        """  def go(xs: List[T]): String {
          |    val me: N[T] = this
          |    return me.f(xs) + "," + f(xs)
          |  }""".stripMargin,
        "return new N[Integer]().go([1])")
      assert(run(src)._1 == Shell.Success("list,list"))
    }

    it("a concrete receiver is unchanged") {
      val src = program(
        "",
        """val n = new N[Integer]()
          |    val ints: List[Integer] = [1]
          |    val nums: List[Number] = [1.5]
          |    return n.f(ints) + "," + n.f(nums)""".stripMargin)
      assert(run(src)._1 == Shell.Success("list,obj"))
    }

    it("with no overload that fits, the error is still E0000 on the argument") {
      val src =
        """class M[T extends Number] {
          |public:
          |  def this {}
          |  def f(xs: List[T]): String = "list"
          |  def f(xs: List[T], n: Int): String = "list2"
          |  def go(xs: List[Number]): String = f(xs)
          |}
          |IO::println(new M[Integer]().go([1.5]))
          |""".stripMargin
      val (result, out) = run(src)
      assert(result.isInstanceOf[Shell.Failure], out)
      assert(out.contains("E0000"), out)
    }
  }
}
