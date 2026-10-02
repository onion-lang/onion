package onion.compiler.tools

import onion.tools.Shell

/**
 * `case x is A.B:` type patterns with a dotted (nested) type name failed because
 * rawTypeDotContinues() in OnionParser.scala (and the corresponding LOOKAHEAD in
 * raw_type() in JJOnionParser.jj) did not include `:` in the set of tokens that
 * can follow the final segment.  The fast-path parser stopped at `A` and then
 * saw `.B:`, producing a spurious syntax error.
 *
 * Exercises:
 *  - `Result.Ok` / `Result.Err` (stdlib nested Java types — two segments)
 *  - `Option.Some` / `Option.None`
 *  - `onion.Result.Ok` (three-segment fully-qualified dotted name)
 */
class DottedTypePatternSpec extends AbstractShellSpec {

  it("matches a stdlib Result.Ok with a dotted type pattern") {
    assert(Shell.Success("ok:42") == shell.run(
      """import { onion.Result }
        |def check(r: Result[Int, String]): String {
        |  select r {
        |    case ok is Result.Ok: return "ok:" + ok.value()
        |    case err is Result.Err: return "err:" + err.error()
        |  }
        |  return "?"
        |}
        |def main(args: String[]): String { return check(Result::ok(42)) }
        |""".stripMargin, "None", Array()))
  }

  it("matches a stdlib Result.Err with a dotted type pattern") {
    assert(Shell.Success("err:oops") == shell.run(
      """import { onion.Result }
        |def check(r: Result[Int, String]): String {
        |  select r {
        |    case ok is Result.Ok: return "ok:" + ok.value()
        |    case err is Result.Err: return "err:" + err.error()
        |  }
        |  return "?"
        |}
        |def main(args: String[]): String { return check(Result::err("oops")) }
        |""".stripMargin, "None", Array()))
  }

  it("matches Option.Some with a dotted type pattern") {
    assert(Shell.Success("some:7") == shell.run(
      """import { onion.Option }
        |def show(o: Option[Int]): String {
        |  select o {
        |    case s is Option.Some: return "some:" + s.value()
        |    case n is Option.None: return "none"
        |  }
        |  return "?"
        |}
        |def main(args: String[]): String { return show(Option::of(7)) }
        |""".stripMargin, "None", Array()))
  }

  it("matches Option.None with a dotted type pattern") {
    assert(Shell.Success("none") == shell.run(
      """import { onion.Option }
        |def show(o: Option[Int]): String {
        |  select o {
        |    case s is Option.Some: return "some:" + s.value()
        |    case n is Option.None: return "none"
        |  }
        |  return "?"
        |}
        |def main(args: String[]): String { return show(Option::none()) }
        |""".stripMargin, "None", Array()))
  }

  it("matches a three-segment dotted type pattern (onion.Result.Ok)") {
    assert(Shell.Success("ok:hello") == shell.run(
      """def check(r: onion.Result[String, String]): String {
        |  select r {
        |    case ok is onion.Result.Ok: return "ok:" + ok.value()
        |    case err is onion.Result.Err: return "err:" + err.error()
        |  }
        |  return "?"
        |}
        |def main(args: String[]): String { return check(onion.Result::ok("hello")) }
        |""".stripMargin, "None", Array()))
  }
}
