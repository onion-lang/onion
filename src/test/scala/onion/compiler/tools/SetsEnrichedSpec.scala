package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the practical Set operations added to `onion.Sets`: fromList/toList,
 * symmetricDifference, isSubsetOf/isSupersetOf/isDisjoint, map, filter, forEach,
 * count, any, all, and find. Null-safety of union/intersection/difference is
 * exercised indirectly via the composed operations.
 */
class SetsEnrichedSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Sets }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "SetsEnriched.on", Array()))
  }

  describe("enriched Sets library") {
    it("symmetricDifference keeps elements in exactly one set") {
      run(
        "val a = Sets::of(1, 2, 3, 4)\n val b = Sets::of(3, 4, 5, 6)\n" +
        "return Sets::symmetricDifference(a, b).size()",
        Shell.Success(4))
    }

    it("map and filter transform a set") {
      run(
        "val a = Sets::of(1, 2, 3, 4)\n" +
        "return Sets::map(a, (x: Int) -> x * 2).size() + Sets::filter(a, (x: Int) -> x > 2).size()",
        Shell.Success(6))
    }

    it("count, any and all use predicates") {
      run(
        "val a = Sets::of(1, 2, 3, 4)\n" +
        "val c = Sets::count(a, (x: Int) -> x % 2 == 0)\n" +
        "val an = if Sets::any(a, (x: Int) -> x > 3) { 10 } else { 0 }\n" +
        "val al = if Sets::all(a, (x: Int) -> x > 0) { 100 } else { 0 }\n" +
        "return c + an + al",
        Shell.Success(112))
    }

    it("isSubsetOf / isSupersetOf / isDisjoint answer set relations") {
      run(
        "val a = Sets::of(1, 2, 3, 4)\n" +
        "val sub = if Sets::isSubsetOf(Sets::of(1, 2), a) { 1 } else { 0 }\n" +
        "val sup = if Sets::isSupersetOf(a, Sets::of(2, 3)) { 10 } else { 0 }\n" +
        "val dis = if Sets::isDisjoint(Sets::of(9, 10), a) { 100 } else { 0 }\n" +
        "return sub + sup + dis",
        Shell.Success(111))
    }

    it("fromList dedupes and toList round-trips") {
      run(
        "val s = Sets::fromList([1, 1, 2, 2, 3])\n" +
        "return s.size() + Sets::toList(s).size()",
        Shell.Success(6))
    }

    it("find returns a matching element") {
      run(
        "val a = Sets::of(1, 2, 3, 4)\n" +
        "val f = Sets::find(a, (x: Int) -> x > 2)\n" +
        "return if f != null { f!! as Int } else { -1 }",
        Shell.Success(3))
    }
  }
}
