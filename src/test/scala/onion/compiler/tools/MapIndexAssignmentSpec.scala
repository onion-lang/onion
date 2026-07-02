package onion.compiler.tools

import onion.tools.Shell

/**
 * Indexed assignment on a Map uses `put`, so `m[k] = v` and `m[k] += v` work
 * (List/array indexed assignment continues to use `set`).
 */
class MapIndexAssignmentSpec extends AbstractShellSpec {
  describe("map indexed assignment") {
    it("assigns and inserts via m[k] = v") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val m = ["a": 1]
          |  m["a"] = 5
          |  m["b"] = 9
          |  return m.get("a") + m.get("b")
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(14) == r)
    }
    it("supports compound assignment m[k] += v") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val m = ["a": 1]
          |  m["a"] += 5
          |  return m.get("a")
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(6) == r)
    }
    it("still uses set for List indexed assignment") {
      val r = shell.run(
        """def main(args: String[]): Int {
          |  val xs = [10, 20]
          |  xs[1] = 99
          |  xs[0] += 5
          |  return xs[0] + xs[1]
          |}""".stripMargin, "None", Array())
      assert(Shell.Success(114) == r)
    }
  }
}
