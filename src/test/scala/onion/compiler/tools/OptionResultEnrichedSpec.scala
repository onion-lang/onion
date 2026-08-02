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
        "val d = some.orNull()\n" +
        "val e = non.orNull()\n" +
        "val dv = d ?: -1\n" +
        "val ev = e ?: -1\n" +
        "return a + b + c + dv + ev",
        Shell.Success(124))
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

    it("of wraps a non-null value and collapses null to none") {
      runInt(
        "val present: Option[Int] = Option::of(42)\n" +
        "val absent: Option[Int] = Option::of(null)\n" +
        "val a = present.isDefined()\n" +
        "val b = absent.isDefined()\n" +
        "return present.getOrElse(-1) + absent.getOrElse(-1) + (if a { 100 } else { 0 }) + (if b { 100 } else { 0 })",
        Shell.Success(141))
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

    it("mapError transforms an Err and passes through an Ok") {
      run(
        "val ok: Result[Int, String] = Result::ok(10)\n" +
        "val er: Result[Int, String] = Result::err(\"boom\")\n" +
        "val a = ok.mapError((e: String) -> e.toUpperCase())\n" +
        "val b = er.mapError((e: String) -> e.toUpperCase())\n" +
        "return a.getOrElse(-1) + \"|\" + a.isOk() + \"|\" + b.getError() + \"|\" + b.isErr()",
        Shell.Success("10|true|BOOM|true"))
    }

    it("orElseGet, exists and toList work on Ok/Err") {
      runInt(
        "val ok: Result[Int, String] = Result::ok(10)\n" +
        "val er: Result[Int, String] = Result::err(\"boom\")\n" +
        "val a = er.orElseGet(() -> -99)\n" +
        "val b = if ok.exists((v: Int) -> v > 5) { 100 } else { 0 }\n" +
        "val c = ok.toList().size() + er.toList().size()\n" +
        "val f = ok.orNull()\n" +
        "val g = er.orNull()\n" +
        "val fv = f ?: -1\n" +
        "val gv = g ?: -1\n" +
        "return a + b + c + fv + gv",
        Shell.Success(11))
    }

    it("ofNullable and trying wrap nullable values and throwing operations") {
      run(
        "val a: Result[String, String] = Result::ofNullable(\"v\", \"boom\")\n" +
        "val b: Result[String, String] = Result::ofNullable(null, \"boom\")\n" +
        "val c: Result[String, Throwable] = Result::trying(() -> \"ok\")\n" +
        "val d: Result[String, Throwable] = Result::trying(() -> { throw new RuntimeException(\"bad\") })\n" +
        "return a.getOrElse(\"X\") + \"|\" + a.isOk() + \"|\" + b.getError() + \"|\" + b.isErr() + \"|\" + " +
        "c.getOrElse(\"X\") + \"|\" + c.isOk() + \"|\" + d.getError().getMessage() + \"|\" + d.isErr()",
        Shell.Success("v|true|boom|true|ok|true|bad|true"))
    }
  }
}
