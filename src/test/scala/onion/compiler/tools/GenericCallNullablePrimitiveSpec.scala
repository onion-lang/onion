package onion.compiler.tools

import onion.tools.Shell

/** A generic call whose result goes into `Int?`/`Long?` infers T as the boxed type (#2666). */
class GenericCallNullablePrimitiveSpec extends AbstractShellSpec {
  private def run(body: String, ret: String, extra: String = ""): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def ident[T](x: T): T = x
         |  static def pick[T](xs: List[T]): T = xs[0]
         |$extra
         |  static def main(args: String[]): $ret {
         |$body
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("generic call into a nullable primitive") {
    it("val a: Int? = ident(5)") {
      assert(Shell.Success(5) == run("    val a: Int? = ident(5)\n    return a ?: -1", "Int"))
    }
    it("val a: Long? = pick([1L])") {
      assert(Shell.Success(1L) == run("    val a: Long? = pick([1L])\n    return a ?: -1L", "Long"))
    }
    it("a return of Int? takes a generic call") {
      val f = "  static def firstOrNull(xs: List[Int]): Int? = if xs.isEmpty() { null } else { pick(xs) }"
      assert(Shell.Success(7) == run("    return firstOrNull([7, 8]) ?: -1", "Int", f))
      assert(Shell.Success(-1) == run("    return firstOrNull([]) ?: -1", "Int", f))
    }
    it("an Int? argument keeps T unboxed, so remove(int) is still chosen") {
      val body =
        """    val w: Int? = 1
          |    val a = new java.util.ArrayList[Int]()
          |    a.add(10)
          |    a.add(20)
          |    a.add(1)
          |    a.remove(ident(w)!!)
          |    return a.toString()""".stripMargin
      assert(Shell.Success("[10, 1]") == run(body, "String"))
    }
    it("T fixed only by an expected Int? stays unboxed, so a lambda's x!! still picks remove(int)") {
      val feed =
        """  static def feed[T](v: Object, f: (T) -> String): T {
          |    f(v as T)
          |    return null
          |  }""".stripMargin
      val body =
        """    val a = new java.util.ArrayList[Int]()
          |    a.add(10)
          |    a.add(20)
          |    a.add(1)
          |    val r: Int? = feed(1) { x -> a.remove(x!!).toString() }
          |    return a.toString()""".stripMargin
      assert(Shell.Success("[10, 1]") == run(body, "String", feed))
    }
    it("an Int? argument into an Int? result") {
      assert(Shell.Success(3) == run("    val w: Int? = 3\n    val r: Int? = ident(w)\n    return r ?: -1", "Int"))
    }
    it("String? still works") {
      assert(Shell.Success("s") == run("    val a: String? = ident(\"s\")\n    return a ?: \"\"", "String"))
    }
  }
}
