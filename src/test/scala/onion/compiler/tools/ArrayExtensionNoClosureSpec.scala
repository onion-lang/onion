package onion.compiler.tools

import onion.tools.Shell

/**
 * A no-closure extension call on an array receiver (e.g. `arr.toList()`) resolves
 * through the extension registry, just like the closure-taking `arr.map { ... }`
 * path already did. Regression test for the array case of
 * selectApplicableExtensionMethod. This makes `Strings::split(...)` /
 * `Strings::words(...)` results flow into the collection pipeline via `.toList()`.
 */
class ArrayExtensionNoClosureSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "ArrayExtNoClosure.on", Array()))
  }

  describe("no-closure extension calls on arrays") {
    it("arr.toList() converts a String[] to a List") {
      runInt(
        "val a = Strings::split(\"a,b,c\", \",\")\n" +
        "return a.toList().size()",
        Shell.Success(3))
    }

    it("a split result flows into the collection pipeline via toList") {
      runInt(
        "val freq = onion.Maps::countBy(Strings::words(\"the fox the dog the\").toList(), (w: String) -> w)\n" +
        "return freq.get(\"the\") as Int",
        Shell.Success(3))
    }

    it("closure array extensions still work alongside no-closure ones") {
      runInt(
        "val a = Strings::split(\"1,2,3,4\", \",\")\n" +
        "return a.map { s => (s as String).length() }.size() + a.toList().size()",
        Shell.Success(8))
    }
  }
}
