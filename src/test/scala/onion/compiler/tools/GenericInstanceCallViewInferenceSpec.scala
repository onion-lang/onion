package onion.compiler.tools

import onion.tools.Shell

/**
 * A plain class reaches a generic parameter through its own applied view of the
 * interface: `String` is a `Comparable[String]`, so `[T](c: Comparable[T])` binds
 * `T = String` for a top-level function and an instance method alike (#2845).
 * Before, the call was E0005 "a function applicable ... is not found".
 */
class GenericInstanceCallViewInferenceSpec extends AbstractShellSpec {
  describe("a generic parameter typed by an interface the argument's class implements") {
    it("binds T through String's Comparable view in a top-level function") {
      val r = shell.run(
        """def id[T](c: Comparable[T]): Comparable[T] = c
          |def main(args: String[]): Int { IO::println(id("a")); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("binds T through the view in an instance method, with T fixed by a second argument") {
      val r = shell.run(
        """class U {
          |public:
          |  def pick[T](c: Comparable[T], d: T): T = d
          |}
          |def main(args: String[]): Int { IO::println(new U().pick("a", "b")); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Success(0) == r)
    }
    it("still rejects a second argument that contradicts the view's T") {
      val r = shell.run(
        """class U {
          |public:
          |  def pick[T](c: Comparable[T], d: T): T = d
          |}
          |def main(args: String[]): Int { IO::println(new U().pick("a", 1)); return 0 }
          |""".stripMargin, "None", Array())
      assert(Shell.Failure(-1) == r)
    }

    def runCapturing(src: String): (Shell.Result, String) = {
      val buf = new java.io.ByteArrayOutputStream()
      val ps = new java.io.PrintStream(buf, true, "UTF-8")
      val (o, e) = (System.out, System.err)
      val r =
        try {
          System.setOut(ps); System.setErr(ps)
          Console.withOut(ps) { Console.withErr(ps) { shell.run(src, "None", Array()) } }
        } finally { System.setOut(o); System.setErr(e) }
      (r, new String(buf.toByteArray, "UTF-8"))
    }
    it("falls back to an Object overload when the view's T contradicts another argument") {
      val (r, out) = runCapturing(
        """def pick[T](c: Comparable[T], d: T): String = "generic"
          |def pick(c: Object, d: Object): String = "object"
          |def main(args: String[]): Int { IO::println(pick("a", 1)); IO::println(pick("a", "b")); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim.linesIterator.toList == List("object", "generic"), out)
    }
    it("falls back to an Object instance overload the same way") {
      val (r, out) = runCapturing(
        """class U {
          |public:
          |  def pick[T](c: Comparable[T], d: T): String = "generic"
          |  def pick(c: Object, d: Object): String = "object"
          |}
          |def main(args: String[]): Int { IO::println(new U().pick("a", 1)); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim == "object", out)
    }
    it("falls back to Object when T sits inside another formal, Comparable[T]") {
      val (r, out) = runCapturing(
        """def pick[T](c: Comparable[T], d: Comparable[T]): String = "generic"
          |def pick(c: Object, d: Object): String = "object"
          |def main(args: String[]): Int { IO::println(pick("a", 1)); IO::println(pick("a", "b")); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim.linesIterator.toList == List("object", "generic"), out)
    }
    it("falls back to Object when T sits inside another formal, List[T]") {
      val (r, out) = runCapturing(
        """import { java.util.* }
          |def pick[T](c: Comparable[T], d: List[T]): String = "generic"
          |def pick(c: Object, d: Object): String = "object"
          |def main(args: String[]): Int {
          |  val xs: List[Integer] = new ArrayList[Integer]()
          |  val ys: List[String] = new ArrayList[String]()
          |  IO::println(pick("a", xs)); IO::println(pick("a", ys)); return 0
          |}
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim.linesIterator.toList == List("object", "generic"), out)
    }
    it("falls back to an Object instance overload when T sits inside another formal") {
      val (r, out) = runCapturing(
        """class U {
          |public:
          |  def pick[T](c: Comparable[T], d: Comparable[T]): String = "generic"
          |  def pick(c: Object, d: Object): String = "object"
          |}
          |def main(args: String[]): Int { IO::println(new U().pick("a", 1)); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim == "object", out)
    }
  }
}
