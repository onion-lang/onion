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

  describe("Csv::parse unterminated quote") {
    it("throws naming the line where the quote was opened") {
      runStr(
        "try {\n" +
        "  Csv::parse(\"id,note\\n1,\\\"oops\\n2,fine\\n\")\n" +
        "  return \"no error\"\n" +
        "} catch e: IllegalArgumentException {\n" +
        "  return e.getMessage()\n" +
        "}",
        Shell.Success("Csv: unterminated quoted field starting at line 2"))
    }

    it("still accepts a closed quoted field spanning lines") {
      runInt(
        "val rows = Csv::parse(\"a,\\\"x\\ny\\\"\\nb,c\\n\")\n" +
        "return rows.size()",
        Shell.Success(2))
    }
  }

  describe("Csv::parse blank-line handling") {
    it("a single trailing newline does not add a phantom extra row") {
      runInt(
        "val rows = Csv::parse(\"a,b\\n1,2\\n\")\n" +
        "return rows.size()",
        Shell.Success(2))
    }

    it("a genuinely blank trailing line becomes its own one-field row, not dropped") {
      runInt(
        "val rows = Csv::parse(\"a,b\\n1,2\\n\\n\")\n" +
        "return rows.size()",
        Shell.Success(3))
    }

    it("the row for a blank trailing line holds one empty-string field") {
      runInt(
        "val rows = Csv::parse(\"a,b\\n1,2\\n\\n\")\n" +
        "val last = rows.get(2)\n" +
        "return last.size()",
        Shell.Success(1))
    }

    it("a blank line in the middle of the data is parsed the same way") {
      runInt(
        "val rows = Csv::parse(\"a,b\\n\\n1,2\\n\")\n" +
        "return rows.size()",
        Shell.Success(3))
    }
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

    it("stringify closes the parse round-trip") {
      runStr(
        "val rows = Csv::parse(\"name,age\\nAlice,30\\nBob,25\")\n" +
        "val out = Csv::stringify(rows)\n" +
        "val back = Csv::parse(out)\n" +
        "return back.get(2).get(0) + \":\" + back.get(2).get(1)",
        Shell.Success("Bob:25"))
    }

    it("stringify quotes fields with commas") {
      runStr(
        "val rows = Csv::parse(\"a,b\\n\\\"x,y\\\",z\")\n" +
        "val out = Csv::stringify(rows)\n" +
        "return Csv::parse(out).get(1).get(0)",
        Shell.Success("x,y"))
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
