package onion.compiler.tools

import onion.tools.Shell

/**
 * When two `extension` blocks target different instantiations of the same
 * generic class (e.g. `extension List[Int]` and `extension List[String]`),
 * the compiler was generating the same container class name for both —
 * `Extension$List` — because `extractTypeName` / `extractTypeDescName` in
 * TypingHeaderPass discarded the type-argument list from a ParameterizedType.
 * This caused E0008 (duplicate class definition) instead of accepting the
 * two extensions as distinct.
 *
 * Fix: include type-argument names in the generated container class name, so
 * `List[Int]` → `Extension$List_Int` and `List[String]` → `Extension$List_String`.
 */
class ExtensionGenericParamNameSpec extends AbstractShellSpec {
  describe("extension blocks on different parameterizations of the same generic class") {
    it("can define extension List[Int] and extension List[String] without a duplicate-class error") {
      val result = shell.run(
        """
          |extension List[Int] {
          |  def intSum(): Int {
          |    return this.fold(0) { acc, x => (acc as Int) + (x as Int) }
          |  }
          |}
          |
          |extension List[String] {
          |  def joinWith(sep: String): String {
          |    var result: String = ""
          |    var first: Boolean = true
          |    foreach s: String in this {
          |      if !first { result = result + sep }
          |      result = result + s
          |      first = false
          |    }
          |    return result
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val nums = [1, 2, 3, 4]
          |    val words = ["a", "b", "c"]
          |    return "" + nums.intSum() + "/" + words.joinWith("-")
          |  }
          |}
          |""".stripMargin,
        "ExtGenericParam.on",
        Array()
      )
      assert(Shell.Success("10/a-b-c") == result)
    }

    it("can define extension Map[String, Int] alongside extension Map[String, String]") {
      val result = shell.run(
        """
          |extension Map[String, Int] {
          |  def totalValues(): Int {
          |    var sum: Int = 0
          |    foreach (k, v) in this {
          |      sum = sum + (v as Int)
          |    }
          |    return sum
          |  }
          |}
          |
          |extension Map[String, String] {
          |  def allKeys(): String {
          |    var s: String = ""
          |    foreach (k, v) in this {
          |      s = s + k
          |    }
          |    return s
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    val m: Map[String, Int] = ["a": 1, "b": 2, "c": 3]
          |    return "" + m.totalValues()
          |  }
          |}
          |""".stripMargin,
        "ExtMapParam.on",
        Array()
      )
      assert(Shell.Success("6") == result)
    }
  }
}
