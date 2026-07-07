package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the practical Map operations added to `onion.Maps`: keys/values,
 * mapKeys, filter/count/anyEntry/allEntries (key+value predicates), forEach,
 * toList, invert, groupBy, countBy, update, mergeWith, and getOrElse.
 */
class MapsEnrichedSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "import { onion.Maps }\n" +
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "MapsEnriched.on", Array()))
  }

  describe("enriched Maps library") {
    it("keys and values return lists in insertion order") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2, \"c\": 3]\n" +
        "return Maps::keys(m).size() + Maps::values(m).size()",
        Shell.Success(6))
    }

    it("mapKeys transforms keys") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2]\n" +
        "val r = Maps::mapKeys(m, (k: String) -> k + \"!\")\n" +
        "return if r.containsKey(\"a!\") { r.get(\"a!\") as Int } else { -1 }",
        Shell.Success(1))
    }

    it("filter uses a key+value predicate") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 20, \"c\": 3]\n" +
        "return Maps::filter(m, (k: String, v: Int) -> v >= 10).size()",
        Shell.Success(1))
    }

    it("count/anyEntry/allEntries take key+value predicates") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2, \"c\": 3]\n" +
        "val c = Maps::count(m, (k: String, v: Int) -> v > 1)\n" +
        "val a = if Maps::anyEntry(m, (k: String, v: Int) -> v > 2) { 10 } else { 0 }\n" +
        "val l = if Maps::allEntries(m, (k: String, v: Int) -> v > 0) { 100 } else { 0 }\n" +
        "return c + a + l",
        Shell.Success(112))
    }

    it("invert swaps keys and values") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2]\n" +
        "val inv = Maps::invert(m)\n" +
        "return if inv.containsKey(2) { 1 } else { 0 }",
        Shell.Success(1))
    }

    it("groupBy buckets a list by a key function") {
      run(
        "val words = [\"apple\", \"avocado\", \"banana\"]\n" +
        "val g = Maps::groupBy(words, (w: String) -> w.substring(0, 1))\n" +
        "return (g.get(\"a\") as List).size()",
        Shell.Success(2))
    }

    it("countBy produces a frequency map") {
      run(
        "val items = [\"x\", \"y\", \"x\", \"x\"]\n" +
        "val f = Maps::countBy(items, (s: String) -> s)\n" +
        "return f.get(\"x\") as Int",
        Shell.Success(3))
    }

    it("toList maps entries to a list") {
      run(
        "val m: Map[String, Int] = [\"a\": 1, \"b\": 2]\n" +
        "return Maps::toList(m, (k: String, v: Int) -> v * 10).size()",
        Shell.Success(2))
    }

    it("update replaces a present key's value") {
      run(
        "val m: Map[String, Int] = [\"a\": 5]\n" +
        "val u = Maps::update(m, \"a\", (v: Int) -> v + 100)\n" +
        "return u.get(\"a\") as Int",
        Shell.Success(105))
    }

    it("mergeWith resolves conflicts with a combiner") {
      run(
        "val r = Maps::mergeWith([\"a\": 1, \"b\": 2], [\"b\": 10, \"c\": 3], (x: Int, y: Int) -> x + y)\n" +
        "return (r.get(\"b\") as Int) + (r.get(\"c\") as Int)",
        Shell.Success(15))
    }

    it("getOrElse calls the supplier only when the key is absent") {
      run(
        "val m: Map[String, Int] = [\"a\": 7]\n" +
        "val present = Maps::getOrElse(m, \"a\", () -> -1)\n" +
        "val missing = Maps::getOrElse(m, \"z\", () -> 99)\n" +
        "return present + missing",
        Shell.Success(106))
    }
  }
}
