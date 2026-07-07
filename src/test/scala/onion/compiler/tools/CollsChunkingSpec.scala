package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the batching/windowing helpers added to `onion.Colls`: chunked,
 * windowed, and slice. These are registered as List extensions, so they chain
 * (`items.chunked(100).map { ... }`).
 */
class CollsChunkingSpec extends AbstractShellSpec {

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "CollsChunking.on", Array()))
  }

  private def runStr(body: String, expect: Shell.Result): Unit = {
    val src =
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "CollsChunkingStr.on", Array()))
  }

  describe("Colls chunking helpers") {
    it("chunked batches a list, last batch may be smaller") {
      runInt(
        "val xs = [1, 2, 3, 4, 5, 6, 7]\n" +
        "val batches = xs.chunked(3)\n" +
        "return batches.size() + (batches.get(2) as List).size()",
        Shell.Success(4)) // 3 batches + last batch of size 1
    }

    it("windowed produces sliding windows of the given size") {
      runInt(
        "val xs = [1, 2, 3, 4, 5]\n" +
        "return xs.windowed(3).size()",
        Shell.Success(3))
    }

    it("slice clamps its bounds") {
      runStr(
        "val xs = [1, 2, 3, 4, 5]\n" +
        "return xs.slice(1, 4).toString() + \"|\" + xs.slice(-2, 100).size().toString()",
        Shell.Success("[2, 3, 4]|5"))
    }

    it("chunked chains into map") {
      runInt(
        "val xs = [1, 2, 3, 4, 5, 6, 7]\n" +
        "return xs.chunked(2).map { b => (b as List).size() }.size()",
        Shell.Success(4))
    }
  }
}
