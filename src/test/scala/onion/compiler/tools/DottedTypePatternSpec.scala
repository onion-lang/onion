package onion.compiler.tools

import onion.tools.Shell

/**
 * `case x is Outer.Inner:` in a select expression should parse the dotted type
 * name as a single reference type. The bug was that `raw_type()` in both the
 * JavaCC grammar and the handwritten fast-path parser stopped consuming dots
 * when the next token after `.ID` was `:` (the case-arm separator), so
 * `Result.Ok` was parsed as just `Result`, leaving `.Ok:` as a syntax error.
 */
class DottedTypePatternSpec extends AbstractShellSpec {

  it("parses a dotted type name in a case-is pattern (Result.Ok)") {
    assert(Shell.Success("ok:42") == shell.run(
      """def main(args: String[]): String {
        |  val r: Result[Int, String] = Result::ok(42)
        |  return select r {
        |    case ok is Result.Ok: "ok:" + ok.value()
        |    else: "other"
        |  }
        |}
        |""".stripMargin, "DottedTypePattern.on", Array()))
  }

  it("parses a dotted type name in a case-is pattern (Result.Err)") {
    assert(Shell.Success("err:oops") == shell.run(
      """def main(args: String[]): String {
        |  val r: Result[Int, String] = Result::err("oops")
        |  return select r {
        |    case err is Result.Err: "err:" + err.error()
        |    else: "other"
        |  }
        |}
        |""".stripMargin, "DottedTypePatternErr.on", Array()))
  }

  it("parses Option.Some in case-is pattern") {
    assert(Shell.Success("some:hello") == shell.run(
      """def main(args: String[]): String {
        |  val o: Option[String] = Option::some("hello")
        |  return select o {
        |    case s is Option.Some: "some:" + s.value()
        |    else: "none"
        |  }
        |}
        |""".stripMargin, "DottedOptionPattern.on", Array()))
  }

  it("parses Option.None in case-is pattern") {
    assert(Shell.Success("none") == shell.run(
      """def main(args: String[]): String {
        |  val o: Option[String] = Option::none()
        |  return select o {
        |    case n is Option.None: "none"
        |    else: "some"
        |  }
        |}
        |""".stripMargin, "DottedOptionNonePattern.on", Array()))
  }

  it("parses a three-segment dotted type in case-is (onion.Result.Ok)") {
    assert(Shell.Success("ok:7") == shell.run(
      """def main(args: String[]): String {
        |  val r: Result[Int, String] = Result::ok(7)
        |  return select r {
        |    case ok is onion.Result.Ok: "ok:" + ok.value()
        |    else: "other"
        |  }
        |}
        |""".stripMargin, "ThreeSegmentDottedType.on", Array()))
  }

  it("resolves a dotted type in an is-expression (not a case arm)") {
    assert(Shell.Success("true") == shell.run(
      """def main(args: String[]): String {
        |  val r: Result[Int, String] = Result::ok(1)
        |  return "" + (r is Result.Ok)
        |}
        |""".stripMargin, "IsExprDottedType.on", Array()))
  }

  it("resolves false for an is-expression on the wrong subtype") {
    assert(Shell.Success("false") == shell.run(
      """def main(args: String[]): String {
        |  val r: Result[Int, String] = Result::ok(1)
        |  return "" + (r is Result.Err)
        |}
        |""".stripMargin, "IsExprDottedTypeFalse.on", Array()))
  }

  it("parses both dotted type patterns as arms (select as expression via else)") {
    assert(Shell.Success("ok:99") == shell.run(
      """def main(args: String[]): String {
        |  val r: Result[Int, String] = Result::ok(99)
        |  return select r {
        |    case ok is Result.Ok: "ok:" + ok.value()
        |    case err is Result.Err: "err:" + err.error()
        |    else: "other"
        |  }
        |}
        |""".stripMargin, "DottedTwoPatternExpr.on", Array()))
  }
}
