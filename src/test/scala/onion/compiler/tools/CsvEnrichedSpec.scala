package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the CSV operations added to `onion.Csv`: stringifyWithHeader
 * (the inverse of parseWithHeader, closing the round-trip), and the column /
 * columnByName extractors.
 */
class CsvEnrichedSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Csv }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "CsvEnrichedInt.on", Array()))
  }

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Csv }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "CsvEnrichedStr.on", Array()))
  }

  describe("enriched Csv") {
    it("columnByName extracts a header-keyed column") {
      runStr(
        "val recs = Csv::parseWithHeader(\"name,age\\nAlice,30\\nBob,25\")\n" +
        "return Csv::columnByName(recs, \"age\").get(0) + \",\" + Csv::columnByName(recs, \"age\").get(1)",
        Shell.Success("30,25"))
    }

    it("column extracts a positional column including the header row") {
      runInt(
        "val rows = Csv::parse(\"name,age\\nAlice,30\\nBob,25\")\n" +
        "return Csv::column(rows, 0).size()",
        Shell.Success(3))
    }

    it("stringifyWithHeader closes the parseWithHeader round-trip") {
      runStr(
        "val recs = Csv::parseWithHeader(\"name,age\\nAlice,30\\nBob,25\")\n" +
        "val out = Csv::stringifyWithHeader(recs)\n" +
        "val back = Csv::parseWithHeader(out)\n" +
        "return back.get(1).get(\"name\") + \":\" + back.get(1).get(\"age\")",
        Shell.Success("Bob:25"))
    }

    it("stringifyWithHeader quotes fields with commas") {
      runStr(
        "val recs = Csv::parseWithHeader(\"a,b\\n\\\"x,y\\\",z\")\n" +
        "val out = Csv::stringifyWithHeader(recs)\n" +
        "return Csv::parseWithHeader(out).get(0).get(\"a\")",
        Shell.Success("x,y"))
    }
  }
}
