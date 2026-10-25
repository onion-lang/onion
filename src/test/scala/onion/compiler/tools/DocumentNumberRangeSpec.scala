package onion.compiler.tools

import onion.tools.Shell

/**
 * A number read out of a JSON/YAML document must not wrap or truncate into a different,
 * plausible number: `3000000000` for an `Int` and `2.9` for an `Int` are defects, and
 * `derive!(Json)` fails the same way it fails on a wrong-typed key (#2381).
 */
class DocumentNumberRangeSpec extends AbstractShellSpec {

  private def run(body: String): Shell.Result =
    shell.run(
      s"""
         |record N(i: Int, s: Short, b: Byte) {
         |  shape doc = json
         |  shape y = yaml
         |}
         |record D(i: Int, s: Short) derive!(Json)
         |class Test {
         |public:
         |  static def main(args: String[]): Int {
         |$body
         |  }
         |}
         |""".stripMargin, "None", Array())

  describe("a json shape") {
    it("rejects an Int out of range") {
      assert(Shell.Success(1) == run(
        "    if N::doc().parse(\"{\\\"i\\\":3000000000,\\\"s\\\":1,\\\"b\\\":1}\").isBad() { return 1 } else { return 0 }"))
    }
    it("rejects a fraction for an Int") {
      assert(Shell.Success(1) == run(
        "    if N::doc().parse(\"{\\\"i\\\":2.9,\\\"s\\\":1,\\\"b\\\":1}\").isBad() { return 1 } else { return 0 }"))
    }
    it("rejects a Byte out of range") {
      assert(Shell.Success(1) == run(
        "    if N::doc().parse(\"{\\\"i\\\":1,\\\"s\\\":1,\\\"b\\\":300}\").isBad() { return 1 } else { return 0 }"))
    }
    it("still reads values in range, and a whole number written 3.0") {
      assert(Shell.Success(1) == run(
        "    if N::doc().parse(\"{\\\"i\\\":-5,\\\"s\\\":32767,\\\"b\\\":-128}\").isBad() { return 0 } else { return 1 }"))
      assert(Shell.Success(1) == run(
        "    if N::doc().parse(\"{\\\"i\\\":3.0,\\\"s\\\":1,\\\"b\\\":1}\").isBad() { return 0 } else { return 1 }"))
    }
  }

  describe("a yaml shape") {
    it("rejects an Int out of range") {
      assert(Shell.Success(1) == run(
        "    if N::y().parse(\"i: 3000000000\\ns: 1\\nb: 1\").isBad() { return 1 } else { return 0 }"))
    }
  }

  describe("derive!(Json)") {
    it("fails on an out-of-range Short instead of wrapping it") {
      assert(Shell.Success(1) == run(
        "    if D::fromJson(\"{\\\"i\\\":1,\\\"s\\\":70000}\") == null { return 1 } else { return 0 }"))
    }
    it("fails on an out-of-range Int") {
      assert(Shell.Success(1) == run(
        "    if D::fromJson(\"{\\\"i\\\":3000000000,\\\"s\\\":1}\") == null { return 1 } else { return 0 }"))
    }
    it("reads values in range") {
      assert(Shell.Success(7) == run(
        "    val d = D::fromJson(\"{\\\"i\\\":3,\\\"s\\\":4}\")!!\n    return d.i() + d.s()"))
    }
  }
}
