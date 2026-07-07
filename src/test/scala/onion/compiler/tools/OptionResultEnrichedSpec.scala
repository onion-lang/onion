package onion.compiler.tools

import onion.tools.Shell

/**
 * Tests for the practical combinators added to `onion.Option`
 * (orElseGet/orNull/orElse/contains/exists/fold/toList) and `onion.Result`
 * (fold/recover/recoverWith/orElseGet/orNull/exists/toList).
 */
class OptionResultEnrichedSpec extends AbstractShellSpec {

  private def run(body: String, expect: Shell.Result): Unit = {
    val src =
      "import {\n  onion.Option\n  onion.Result\n}\n" +
      "class Test {\npublic:\n  static def main(args: String[]): String {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "OptionResultEnriched.on", Array()))
  }

  private def runInt(body: String, expect: Shell.Result): Unit = {
    val src =
      "import {\n  onion.Option\n  onion.Result\n}\n" +
      "class Test {\npublic:\n  static def main(args: String[]): Int {\n" + body + "\n  }\n}\n"
    assert(expect == shell.run(src, "OptionResultEnrichedInt.on", Array()))
  }

  describe("enriched Option") {
    it("orElseGet, orNull and orElse handle absence") {
      runInt(
        "val some: Option[Int] = Option::some(42)\n" +
        "val non: Option[Int] = Option::none()\n" +
        "val a = some.orElseGet(() -> -1)\n" +
        "val b = non.orElseGet(() -> -1)\n" +
        "val c = non.orElse(some).getOrElse(0)\n" +
        "return a + b + c",
        Shell.Success(83))
    }

    it("contains, exists and fold inspect the value") {
      run(
        "val some: Option[Int] = Option::some(42)\n" +
        "val non: Option[Int] = Option::none()\n" +
        "val ct = if some.contains(42) { \"C\" } else { \"\" }\n" +
        "val ex = if some.exists((x: Int) -> x > 40) { \"E\" } else { \"\" }\n" +
        "return ct + ex + some.fold(() -> \"none\", (x: Int) -> \"v\") + non.fold(() -> \"none\", (x: Int) -> \"v\")",
        Shell.Success("CEvnone"))
    }

    it("toList holds zero or one element") {
      runInt(
        "val some: Option[Int] = Option::some(42)\n" +
        "val non: Option[Int] = Option::none()\n" +
        "return some.toList().size() + non.toList().size()",
        Shell.Success(1))
    }
  }

  describe("enriched Result") {
    it("fold collapses Ok and Err") {
      run(
        "val ok: Result[Int, String] = Result::ok(10)\n" +
        "val er: Result[Int, String] = Result::err(\"boom\")\n" +
        "return ok.fold((e: String) -> \"e\", (v: Int) -> \"ok\") + \"|\" + er.fold((e: String) -> \"e\", (v: Int) -> \"ok\")",
        Shell.Success("ok|e"))
    }

    it("recover and recoverWith rescue an Err") {
      runInt(
        "val er: Result[Int, String] = Result::err(\"boom\")\n" +
        "val ok: Result[Int, String] = Result::ok(5)\n" +
        "val r1 = er.recover((e: String) -> -1).getOrElse(0)\n" +
        "val r2 = er.recoverWith((e: String) -> Result::ok(7)).getOrElse(0)\n" +
        "val r3 = ok.recover((e: String) -> -1).getOrElse(0)\n" +
        "return r1 + r2 + r3",
        Shell.Success(11))
    }

    it("orElseGet, exists and toList work on Ok/Err") {
      runInt(
        "val ok: Result[Int, String] = Result::ok(10)\n" +
        "val er: Result[Int, String] = Result::err(\"boom\")\n" +
        "val a = er.orElseGet(() -> -99)\n" +
        "val b = if ok.exists((v: Int) -> v > 5) { 100 } else { 0 }\n" +
        "val c = ok.toList().size() + er.toList().size()\n" +
        "return a + b + c",
        Shell.Success(2))
    }
  }
}
