package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/** `foreach (a, b) in xs` over a non-Map names `entrySet()` and `K, V` (issue #2301); now E0103. */
class ForeachPairNonMapSpec extends AbstractShellSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.code)
      case _ => Seq.empty
    }
  }

  describe("foreach (a, b) over a non-Map") {
    it("reports only E0103 for a List of records") {
      val c = codes("record P(x: Int, y: Int)\nval ps = [new P(1, 2), new P(3, 4)]\nforeach (x, y) in ps { println(x + y) }")
      assert(c == Seq("E0103"), c)
    }
    it("reports E0103 for an array") {
      val c = codes("val xs = new Int[2]\nforeach (a, b) in xs { println(a) }")
      assert(c == Seq("E0103"), c)
    }
    it("still walks the entries of a class that declares its own entrySet() (panel repro on #2354)") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Bag {
          |  val m: HashMap[String, Int]
          |public:
          |  def this { m = new HashMap[String, Int](); m.put("a", 1); m.put("b", 2) }
          |  def entrySet(): Set[Map.Entry[String, Int]] = m.entrySet()
          |}
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b = new Bag()
          |    var total = 0
          |    foreach (k, v) in b { total = total + v }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "None", Array())
      assert(Shell.Success(3) == result)
    }
    it("still walks the entries an extension entrySet() provides (panel repro on #2354 at 2edd5af)") {
      val result = shell.run(
        """
          |import { java.util.* }
          |class Bag {
          |  val m: HashMap[String, Int]
          |public:
          |  def this { m = new HashMap[String, Int](); m.put("a", 1); m.put("b", 2) }
          |  def getM(): HashMap[String, Int] = m
          |}
          |extension Bag { def entrySet(): Set[Map.Entry[String, Int]] = this.getM().entrySet() }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val b = new Bag()
          |    var total = 0
          |    foreach (k, v) in b { total = total + v }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "None", Array())
      assert(Shell.Success(3) == result)
    }
    it("does not report E0103 for a List whose entrySet() comes from an extension") {
      val c = codes(
        """import { java.util.* }
          |extension List[String] {
          |  def entrySet(): Set[Map.Entry[String, Int]] {
          |    val m = new HashMap[String, Int]()
          |    foreach s in this { m.put(s, s.length()) }
          |    return m.entrySet()
          |  }
          |}
          |val xs: List[String] = ["ab", "c"]
          |foreach (k, v) in xs { println(k + v) }
          |""".stripMargin)
      assert(!c.contains("E0103"), c)
    }
    it("still walks a Map's entries") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    val m = new HashMap[String, Int]()
          |    m.put("a", 1)
          |    m.put("b", 2)
          |    var total = 0
          |    foreach (k, v) in m { total = total + v }
          |    return total
          |  }
          |}
          |""".stripMargin,
        "None", Array())
      assert(Shell.Success(3) == result)
    }
      it("reports only E0103 for a call result (panel block at 92b6be8)") {
      val c = codes("record P(x: Int, y: Int)\ndef getPs(): List[P] = [new P(1, 2)]\nforeach (x, y) in getPs() { println(x + y) }")
      assert(c == Seq("E0103"), c)
    }
    it("reports only E0103 for a list literal (panel block at 92b6be8)") {
      val c = codes("record P(x: Int, y: Int)\nforeach (x, y) in [new P(1, 2)] { println(x + y) }")
      assert(c == Seq("E0103"), c)
    }
    it("reports a broken target's own error once, with no entrySet() or K, V error") {
      val c = codes("foreach (k, v) in undefinedThing { println(k) }")
      assert(c == Seq("E0002"), c)
    }
    it("keeps the null-safety error for a nullable Map") {
      val c = codes("import { java.util.* }\nval m: Map[String, Int]? = null\nforeach (k, v) in m { println(k) }")
      assert(c == Seq("E0070"), c)
    }
    it("still walks the entries of a Map returned by a call, typing the call once") {
      val result = shell.run(
        """
          |import { java.util.HashMap }
          |class Test {
          |  static var calls: Int = 0
          |public:
          |  static def make(): HashMap[String, Int] {
          |    calls = calls + 1
          |    val m = new HashMap[String, Int]()
          |    m.put("a", 1)
          |    m.put("b", 2)
          |    return m
          |  }
          |  static def main(args: String[]): Int {
          |    var total = 0
          |    foreach (k, v) in make() { total = total + v }
          |    return total * 10 + calls
          |  }
          |}
          |""".stripMargin,
        "None", Array())
      assert(Shell.Success(31) == result)
    }
  }
}
