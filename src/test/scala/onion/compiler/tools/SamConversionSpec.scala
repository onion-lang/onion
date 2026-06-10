package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for SAM conversion (issue #127): closures implement a Java
 * functional interface's single abstract method when the target has no
 * FunctionN-style `call`, plus the vararg type-argument inference fix
 * that makes Colls::mutableListOf(1,2,3) a List[Integer].
 */
class SamConversionSpec extends AbstractShellSpec {

  describe("SAM conversion") {
    it("implements Runnable from a zero-arg lambda") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuffer()
          |    val r: Runnable = () -> { sb.append("ran"); }
          |    r.run()
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "SamRunnable.on",
        Array()
      )
      assert(Shell.Success("ran") == result)
    }

    it("implements Comparator and sorts through Collections.sort") {
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val cmp: Comparator[Integer] = (a, b) -> (b as Int) - (a as Int)
          |    val xs = Colls::mutableListOf(1, 3, 2)
          |    Collections::sort(xs, cmp)
          |    return xs.toString()
          |  }
          |}
          |""".stripMargin,
        "SamComparator.on",
        Array()
      )
      assert(Shell.Success("[3, 2, 1]") == result)
    }

    it("passes a SAM value to a Java constructor (Thread)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): String {
          |    val sb = new StringBuffer()
          |    val r: Runnable = () -> { sb.append("threaded"); }
          |    val t = new Thread(r)
          |    t.start()
          |    t.join()
          |    return sb.toString()
          |  }
          |}
          |""".stripMargin,
        "SamThread.on",
        Array()
      )
      assert(Shell.Success("threaded") == result)
    }

    it("ignores abstract Object-method redeclarations when finding the SAM") {
      // Comparator also declares equals(Object); compare must still be chosen
      val result = shell.run(
        """
          |import { java.util.Comparator }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val cmp: Comparator[String] = (a, b) -> a.length() - b.length()
          |    return cmp.compare("ab", "a")
          |  }
          |}
          |""".stripMargin,
        "SamObjectMethods.on",
        Array()
      )
      assert(Shell.Success(1) == result)
    }
  }

  describe("Vararg type-argument inference") {
    it("infers List[Integer] from mutableListOf(1, 2, 3)") {
      val result = shell.run(
        """
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val xs = Colls::mutableListOf(10, 20, 30)
          |    val v: Int = xs.get(1)
          |    return v + xs.size()
          |  }
          |}
          |""".stripMargin,
        "VarargInference.on",
        Array()
      )
      assert(Shell.Success(23) == result)
    }
  }
}
