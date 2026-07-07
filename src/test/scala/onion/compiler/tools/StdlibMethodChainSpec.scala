package onion.compiler.tools

import onion.tools.Shell

/**
 * The enriched Maps and Strings helpers are registered as builtin extension
 * methods on their receiver type (Map / String), so they can be written as
 * method chains — `m.filter { ... }.mapValues { ... }`, `s.capitalize()` —
 * instead of only as static `Maps::` / `Strings::` calls. A user-declared
 * `extension` of the same name still shadows the builtin (see TextAnalyzer.on).
 */
class StdlibMethodChainSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StdlibChainInt.on", Array()))
  }

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "StdlibChainStr.on", Array()))
  }

  describe("Map method chaining (Maps extension)") {
    it("chains filter and mapValues on a map") {
      runInt(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2, \"c\": 3]\n" +
        "val r = m.filter((k: String, v: Int) -> v > 1).mapValues((v: Int) -> v * 10)\n" +
        "return r.size() + (r.get(\"b\") as Int)",
        Shell.Success(22))
    }

    it("calls keys and invert as methods") {
      runInt(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2]\n" +
        "val ks = m.keys().size()\n" +
        "val inv = if m.invert().containsKey(1) { 10 } else { 0 }\n" +
        "return ks + inv",
        Shell.Success(12))
    }

    it("groupBy and countBy chain off a list") {
      runInt(
        "val words = [\"apple\", \"avocado\", \"banana\"]\n" +
        "return words.groupBy((w: String) -> w.substring(0, 1)).size() + " +
        "(words.countBy((w: String) -> w.substring(0, 1)).get(\"a\") as Int)",
        Shell.Success(4))
    }
  }

  describe("String method chaining (Strings extension)") {
    it("chains trim and truncate, with a Java method in between") {
      runStr(
        "return \"  Hello World  \".trim().truncate(8, \"...\")",
        Shell.Success("Hello..."))
    }

    it("capitalize and capitalizeWords work as methods") {
      runStr(
        "return \"hello\".capitalize() + \"|\" + \"a b\".capitalizeWords()",
        Shell.Success("Hello|A B"))
    }

    it("null-safe parser chains with elvis") {
      runInt(
        "return (\"42\".toIntOrNull() ?: -1) + (\"nope\".toIntOr(7))",
        Shell.Success(49))
    }
  }
}
