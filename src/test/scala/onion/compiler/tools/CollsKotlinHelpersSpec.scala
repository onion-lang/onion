package onion.compiler.tools

import onion.tools.Shell
import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import java.io.StringReader

/**
 * List helpers a script reaches for that were E0005: `associateBy` (index a list by a key),
 * `indexOfFirst` (position of the first match, -1 if none), and `maxOrNull`/`minOrNull`
 * (`max()`/`min()` throw on an empty list). `firstOrNull`/`lastOrNull` already exist as
 * `first`/`last`, which return null on an empty list, so those names now hint at them.
 */
class CollsKotlinHelpersSpec extends AbstractShellSpec {
  private def eval(body: String): Shell.Result =
    shell.run(
      s"""
         |class Test {
         |public:
         |  static def main(args: String[]): String {
         |    $body
         |  }
         |}
         |""".stripMargin,
      "None",
      Array()
    )

  private def messagesOf(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  describe("new List helpers") {
    it("associateBy keys each element, the last one winning a shared key") {
      assert(eval("""return "" + ["apple", "bob", "cat"].associateBy { w -> w.length() }""") ==
        Shell.Success("{5=apple, 3=cat}"))
    }
    it("associateBy keeps the element type for lookups") {
      assert(eval("""val m = ["ab", "c"].associateBy { w -> w.substring(0, 1) }
                    |return "" + m.get("a").length()""".stripMargin) == Shell.Success("2"))
    }
    it("indexOfFirst finds the first match, or -1") {
      assert(eval("""return "" + [5, 8, 9].indexOfFirst { x -> x > 6 } + [1].indexOfFirst { x -> x > 6 }""") ==
        Shell.Success("1-1"))
    }
    it("maxOrNull/minOrNull give null on an empty list") {
      assert(eval("""val e: List[Int] = []
                    |return "" + [3, 7, 5].maxOrNull() + [3, 7, 5].minOrNull() + (e.maxOrNull() ?: -1) + (e.minOrNull() ?: -2)""".stripMargin) ==
        Shell.Success("73-1-2"))
    }
  }

  describe("names hinting at first/last") {
    it("firstOrNull hints at first") {
      assert(messagesOf("val xs: List[Int] = [1]\nprintln(xs.firstOrNull())\n").contains(": first"))
    }
    it("lastOrNull hints at last") {
      assert(messagesOf("val xs: List[Int] = [1]\nprintln(xs.lastOrNull())\n").contains(": last"))
    }
  }
}
