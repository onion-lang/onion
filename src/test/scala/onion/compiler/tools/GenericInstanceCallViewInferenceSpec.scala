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
    it("does not look for T inside the view's own type argument") {
      val (r, out) = runCapturing(
        """import { java.util.* }
          |class LC conforms Comparable[List[String]] {
          |public:
          |  def this {}
          |  def compareTo(o: List[String]): Int = 0
          |}
          |def pick[T](c: Comparable[List[T]], d: T): String = "generic"
          |def pick(c: Object, d: Object): String = "object"
          |def main(args: String[]): Int { IO::println(pick(new LC(), 1)); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim == "object", out)
    }
    // The view's argument may be a superclass of the argument: `Timestamp` is a
    // `Comparable[Date]`. Selection binds T = Date, and so must the call (javac does too).
    it("binds T to the view's superclass argument, in either argument order") {
      val (r, out) = runCapturing(
        """import { java.sql.Timestamp }
          |class U {
          |public:
          |  def later[T](a: Comparable[T], b: T): String = "generic"
          |  def later(a: Object, b: Object): String = "object"
          |  def rev[T](b: T, a: Comparable[T]): String = "generic"
          |  def rev(b: Object, a: Object): String = "object"
          |  static def slater[T](a: Comparable[T], b: T): String = "generic"
          |  static def slater(a: Object, b: Object): String = "object"
          |}
          |def later[T](a: Comparable[T], b: T): String = "generic"
          |def later(a: Object, b: Object): String = "object"
          |def main(args: String[]): Int {
          |  val t = new Timestamp(0L)
          |  IO::println(new U().later(t, t)); IO::println(new U().rev(t, t))
          |  IO::println(U::slater(t, t)); IO::println(later(t, t))
          |  IO::println(new U().later(t, "x"))
          |  return 0
          |}
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim.linesIterator.toList == List("generic", "generic", "generic", "generic", "object"), out)
    }
    it("binds T to a user superclass that is Comparable to itself") {
      val (r, out) = runCapturing(
        """class Money conforms Comparable[Money] {
          |public:
          |  def this {}
          |  def compareTo(o: Money): Int = 0
          |}
          |class Euro extends Money {
          |public:
          |  def this {}
          |}
          |class U {
          |public:
          |  def later[T](a: Comparable[T], b: T): T = b
          |  def later(a: Object, b: Object): Object = "object"
          |}
          |def main(args: String[]): Int {
          |  val e = new Euro()
          |  val m: Money = new U().later(e, e)
          |  IO::println(m === e); return 0
          |}
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim == "true", out)
    }
    it("checks every vararg element against the view's T") {
      val (r, out) = runCapturing(
        """def f[T](xs: Comparable[T]...): String = "generic"
          |def f(xs: Object...): String = "object"
          |def main(args: String[]): Int { IO::println(f("a", 1)); IO::println(f("a", "b")); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim.linesIterator.toList == List("object", "generic"), out)
    }
    it("does not apply the view inside a type argument of the argument") {
      val (r, out) = runCapturing(
        """import { java.util.* }
          |def f[T](xs: List[Comparable[T]]): String = "generic"
          |def f(xs: Object): String = "object"
          |def main(args: String[]): Int {
          |  val xs: List[String] = ["a", "b"]
          |  IO::println(f(xs)); return 0
          |}
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim == "object", out)
    }
  
  describe("an Onion enum argument, read as Comparable of itself as javac does") {
    val withObject =
      """def id(a: Object): String = "object"
        |""".stripMargin
    def topLevel(extra: String) =
      s"""enum Color { RED, GREEN }
         |def id[T](a: Comparable[T]): String = "generic"
         |$extra
         |def main(args: String[]): Int { val c = Color::RED; IO::println(id(c)); return 0 }
         |""".stripMargin
    def instance(extra: String) =
      s"""enum Color { RED, GREEN }
         |class U {
         |public:
         |  def id[T](a: Comparable[T]): String = "generic"
         |  $extra
         |}
         |def main(args: String[]): Int { IO::println(new U().id(Color::RED)); return 0 }
         |""".stripMargin
    def static(extra: String) =
      s"""enum Color { RED, GREEN }
         |class U {
         |public:
         |  static def id[T](a: Comparable[T]): String = "generic"
         |  ${extra.replace("def id(", "static def id(")}
         |}
         |def main(args: String[]): Int { IO::println(U::id(Color::RED)); return 0 }
         |""".stripMargin
    for (form, src) <- List("top-level" -> topLevel, "instance" -> instance, "static" -> static) do
      it(s"binds T = the enum in the $form form, with an Object overload") {
        val (r, out) = runCapturing(src(withObject))
        assert(Shell.Success(0) == r, out)
        assert(out.trim == "generic", out)
      }
      it(s"binds T = the enum in the $form form, with no Object overload") {
        val (r, out) = runCapturing(src(""))
        assert(Shell.Success(0) == r, out)
        assert(out.trim == "generic", out)
      }
    it("binds T = the enum when a second argument is the bare T") {
      val (r, out) = runCapturing(
        """enum Color { RED, GREEN }
          |def later[T](a: Comparable[T], b: T): T = b
          |def main(args: String[]): Int { IO::println(later(Color::RED, Color::GREEN)); return 0 }
          |""".stripMargin)
      assert(Shell.Success(0) == r, out)
      assert(out.trim == "GREEN", out)
    }
  }
}
}
