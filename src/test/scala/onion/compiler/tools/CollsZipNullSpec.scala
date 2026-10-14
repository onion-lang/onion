package onion.compiler.tools

import onion.tools.Shell

/**
 * Regression for #2109: `Colls::zip` built each pair with `java.util.List.of`,
 * which throws NullPointerException on a null element, unlike every other List
 * pipeline helper (map/filter/fold/groupBy), which are plain ArrayList-backed
 * and tolerate null elements fine.
 */
class CollsZipNullSpec extends AbstractShellSpec {

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "CollsZipNull.on", Array()))
  }

  describe("Colls::zip with a null element") {
    it("pairs a null from either side instead of crashing") {
      runStr(
        "val a: List[String?] = [\"x\", null, \"z\"]\n" +
        "val b: List[Int] = [1, 2, 3]\n" +
        "val zipped = Colls::zip(a, b)\n" +
        "val pair2 = zipped[1] as List\n" +
        "return if zipped.size() == 3 && pair2[0] == null && (pair2[1] as Int) == 2 { \"ok\" } else { \"fail\" }",
        Shell.Success("ok"))
    }

    it("pairs a null on the second list too") {
      runStr(
        "val a: List[Int] = [1, 2, 3]\n" +
        "val b: List[String?] = [\"x\", null, \"z\"]\n" +
        "val zipped = Colls::zip(a, b)\n" +
        "val pair2 = zipped[1] as List\n" +
        "return if (pair2[0] as Int) == 2 && pair2[1] == null { \"ok\" } else { \"fail\" }",
        Shell.Success("ok"))
    }
  }
}
