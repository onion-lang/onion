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

  describe("Csv::parse leading UTF-8 BOM") {
    it("treats a leading U+FEFF as an encoding marker, not part of the first header") {
      runStr(
        "val rows = Csv::parseWithHeader(\"\\uFEFFname,age\\nann,3\\n\")\n" +
        "return \"\" + rows[0].containsKey(\"name\") + \" \" + rows[0][\"name\"]",
        Shell.Success("true ann"))
    }
  }

  describe("Csv::parseWithHeader repeated header") {
    it("throws naming the header and both columns instead of dropping a value") {
      runStr(
        "try {\n" +
        "  Csv::parseWithHeader(\"h,h,k\\n1,2,3\\n\")\n" +
        "  return \"no error\"\n" +
        "} catch e: IllegalArgumentException {\n" +
        "  return e.getMessage()\n" +
        "}",
        Shell.Success("Csv: header \"h\" appears in column 1 and column 2 (the one kept), so data row 1 would lose its value in column 1; rename one, or read the rows positionally with Csv::parse"))
    }
    it("treats a second blank header as a repeat too") {
      runStr(
        "try {\n" +
        "  Csv::parseWithHeader(\"name,,\\nAda,x,y\\n\")\n" +
        "  return \"no error\"\n" +
        "} catch e: IllegalArgumentException {\n" +
        "  return \"error\"\n" +
        "}",
        Shell.Success("error"))
    }
    it("reads repeated blank headers whose columns are empty in every row") {
      runStr(
        "val rows = Csv::parseWithHeader(\"name,age,,\\nAda,36,,\\nBob,40,,\\n\")\n" +
        "return rows[0][\"name\"] + rows[1][\"name\"] + rows.size",
        Shell.Success("AdaBob2"))
    }
    it("reads a header-only text with a repeated header as no rows") {
      runInt(
        "return Csv::parseWithHeader(\"a,b,a\\n\").size",
        Shell.Success(0))
    }
    it("reads a repeat whose earlier column is empty, keeping the later value") {
      runStr(
        "val rows = Csv::parseWithHeader(\"h,h\\n,2\\n\")\n" +
        "return rows[0][\"h\"]",
        Shell.Success("2"))
    }
    it("reads a repeat whose cells hold the same value as the kept column") {
      runStr(
        "return Csv::parseWithHeader(\"id,name,id\\n1,Ada,1\\n\").toString()",
        Shell.Success("[{id=1, name=Ada}]"))
    }
    it("reads three repeats when every non-empty cell equals the last one") {
      runStr(
        "val rows = Csv::parseWithHeader(\"h,h,h\\n1,,1\\n\")\n" +
        "return rows[0][\"h\"]",
        Shell.Success("1"))
    }
    it("throws for three repeats when an earlier value differs, naming the kept column") {
      runStr(
        "try {\n" +
        "  Csv::parseWithHeader(\"h,h,h\\n1,1,2\\n\")\n" +
        "  return \"no error\"\n" +
        "} catch e: IllegalArgumentException {\n" +
        "  return e.getMessage()\n" +
        "}",
        Shell.Success("Csv: header \"h\" appears in column 1 and column 3 (the one kept), so data row 1 would lose its value in column 1; rename one, or read the rows positionally with Csv::parse"))
    }
    it("throws when the row is too short to reach the kept column") {
      runStr(
        "try {\n" +
        "  Csv::parseWithHeader(\"h,k,h\\n1,2\\n\")\n" +
        "  return \"no error\"\n" +
        "} catch e: IllegalArgumentException {\n" +
        "  return \"error\"\n" +
        "}",
        Shell.Success("error"))
    }
    it("names a repeated blank header as a blank header") {
      runStr(
        "try {\n" +
        "  Csv::parseWithHeader(\"name,,\\nAda,x,y\\n\")\n" +
        "  return \"no error\"\n" +
        "} catch e: IllegalArgumentException {\n" +
        "  return e.getMessage()\n" +
        "}",
        Shell.Success("Csv: a blank header appears in column 2 and column 3 (the one kept), so data row 1 would lose its value in column 2; rename one, or read the rows positionally with Csv::parse"))
    }
    it("still reads distinct headers") {
      runStr(
        "val rows = Csv::parseWithHeader(\"a,b\\n1,2\\n\")\n" +
        "return rows[0][\"a\"] + rows[0][\"b\"]",
        Shell.Success("12"))
    }
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
