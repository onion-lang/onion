package onion.compiler.tools

import onion.tools.Shell

/**
 * A `select` over a boxed primitive (a `List[Int]` element, a range's loop variable)
 * matches primitive case labels by value, as `==` does (issue #2933). It used to
 * reject every label with E0000 "type Int is expected, but type Int is used".
 */
class SelectBoxedScrutineeSpec extends AbstractShellSpec {
  private def run(body: String): Shell.Result =
    shell.run(s"def main(args: String[]): String {\n$body\n}", "None", Array())

  it("matches an int label against a range's loop variable, with a continue arm") {
    assert(Shell.Success("1,3,4,5,6,") == run(
      "val sb = new StringBuilder()\n" +
      "foreach i in 1..6 {\n val y = select i {\n case 2: continue\n else: i\n }\n sb.append(y).append(\",\")\n}\n" +
      "return sb.toString()"))
  }
  it("matches an int label against a List[Int] element") {
    assert(Shell.Success("two") == run(
      "val xs: List[Int] = [1, 2, 3]\n" +
      "return select xs[1] {\n case 1: \"one\"\n case 2: \"two\"\n else: \"?\"\n}"))
  }
  it("matches several labels in one case and falls to else on a miss") {
    assert(Shell.Success("small,small,big,") == run(
      "val sb = new StringBuilder()\n" +
      "foreach i in [1, 2, 9] {\n sb.append(select i {\n case 1, 2: \"small\"\n else: \"big\"\n }).append(\",\")\n}\n" +
      "return sb.toString()"))
  }
  it("widens an int label for a boxed Long scrutinee") {
    assert(Shell.Success("two") == run(
      "val xs: List[Long] = [1L, 2L]\n" +
      "return select xs[1] {\n case 2: \"two\"\n else: \"?\"\n}"))
  }
  it("compares a Double label against a boxed Double by value") {
    assert(Shell.Success("half") == run(
      "val xs: List[Double] = [0.5]\n" +
      "return select xs[0] {\n case 0.5: \"half\"\n else: \"?\"\n}"))
  }
  it("matches a char label against a boxed Char") {
    assert(Shell.Success("b") == run(
      "val xs: List[Char] = ['a', 'b']\n" +
      "return select xs[1] {\n case 'a': \"a\"\n case 'b': \"b\"\n else: \"?\"\n}"))
  }
  it("still rejects a label of an unrelated type") {
    assert(Shell.Failure(-1) == run(
      "val xs: List[Int] = [1]\n" +
      "return select xs[0] {\n case \"x\": \"bad\"\n else: \"ok\"\n}"))
  }
  it("still rejects a Long label against a boxed Int (no narrowing)") {
    assert(Shell.Failure(-1) == run(
      "val xs: List[Int] = [1]\n" +
      "return select xs[0] {\n case 1L: \"bad\"\n else: \"ok\"\n}"))
  }
}
