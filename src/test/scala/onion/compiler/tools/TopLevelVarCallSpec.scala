package onion.compiler.tools

import onion.tools.Shell

/** `g()` on a top-level `var` holding a function used to read the static field as an instance field (#3038). */
class TopLevelVarCallSpec extends AbstractShellSpec {
  private def run(body: String): (Shell.Result, String) = {
    val buf = new java.io.ByteArrayOutputStream()
    val old = System.out
    System.setOut(new java.io.PrintStream(buf, true, "UTF-8"))
    val r = try shell.run(body, "None", Array()) finally System.setOut(old)
    (r, buf.toString("UTF-8").trim)
  }

  describe("calling a top-level var that holds a function") {
    it("works for a zero-argument function") {
      val result = run(
        """var g: () -> Int = () -> 5
          |IO::println(g())""".stripMargin)
      assert(result == ((Shell.Success(null), "5")))
    }

    it("works for a one-argument function and sees reassignment") {
      val result = run(
        """var g = (x: Int) -> x + 1
          |g = (x: Int) -> x * 10
          |IO::println(g(4))""".stripMargin)
      assert(result == ((Shell.Success(null), "40")))
    }

    it("still works for a val") {
      val result = run(
        """val g: () -> Int = () -> 7
          |IO::println(g())""".stripMargin)
      assert(result == ((Shell.Success(null), "7")))
    }
  }

  describe("calling an instance field that holds a function") {
    it("works from a lambda in an instance method") {
      val result = run(
        """class C {
          |  var inf: () -> Int = () -> 4
          |public:
          |  def this {}
          |  def k(): Int { val l: () -> Int = () -> inf(); return l() }
          |}
          |IO::println(new C().k())""".stripMargin)
      assert(result == ((Shell.Success(null), "4")))
    }

    it("works from a nested lambda and passes arguments") {
      val result = run(
        """class C {
          |  var add: (Int) -> Int = (x: Int) -> x + 1
          |public:
          |  def this {}
          |  def k(): Int {
          |    val outer: () -> Int = () -> { val inner: () -> Int = () -> add(9); inner() }
          |    return outer()
          |  }
          |}
          |IO::println(new C().k())""".stripMargin)
      assert(result == ((Shell.Success(null), "10")))
    }
  }
}
