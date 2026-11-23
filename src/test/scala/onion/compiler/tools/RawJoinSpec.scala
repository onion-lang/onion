package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, WarningLevel}
import onion.tools.Shell

import java.io.StringReader

/**
 * Issue #3255 (and #3247): branches of one generic class with different type
 * arguments joined as the raw class, which fits every instantiation. A join that
 * reaches a call argument, a non-generic lambda result or an untyped local is no
 * longer an `Option[Int]`.
 */
class RawJoinSpec extends AbstractShellSpec {

  private val header =
    "import { onion.Option; java.util.List; }\n" +
      "val tail: Option[Int] = Option::none()\n" +
      "val other: Option[String] = Option::some(\"str\")\n"

  private def run(body: String, name: String) = shell.run(header + body, name, Array())

  /** Error codes with their line and column, as `code@line:column`. */
  private def errors(body: String): Seq[String] = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On)
    val result = new OnionCompiler(config)
      .compileDetailed(Seq(new StreamInputSource(() => new StringReader(header + body), "J.on")))
    result.allErrors.map { e =>
      val at = if (e.location == null) "-1" else s"${e.location.line}:${e.location.column}"
      s"${e.errorCode.getOrElse("?")}@$at"
    }.toSeq
  }

  describe("a join of Option[String] and Option[Int] is not an Option[Int] (#3255)") {
    it("a lambda for a non-generic function parameter") {
      val r = run(
        """def chk(f: () -> Option[Int]): Option[Int] = f()
          |val c: Option[Int] = chk { if 1 < 2 { other } else { tail } }
          |def main(args: String[]): String = "" + (c.get() + 1)
          |""".stripMargin, "RawJoinLambda.on")
      assert(Shell.Failure(-1) == r)
    }
    it("an untyped local") {
      val r = run(
        """val x = if 1 < 2 { other } else { tail }
          |val c: Option[Int] = x
          |def main(args: String[]): String = "" + (c.get() + 1)
          |""".stripMargin, "RawJoinLocal.on")
      assert(Shell.Failure(-1) == r)
    }
    it("a call argument, for a function, a method and a constructor (#3247)") {
      val fn = run(
        """def f(x: Option[Int]): Option[Int] = x
          |def main(args: String[]): String = "" + f(if 1 < 2 { other } else { tail }).get()
          |""".stripMargin, "RawJoinArgFn.on")
      assert(Shell.Failure(-1) == fn)
      val st = run(
        """class K { public: static def g(x: Option[Int]): Option[Int] = x }
          |def main(args: String[]): String = "" + K::g(if 1 < 2 { other } else { tail }).get()
          |""".stripMargin, "RawJoinArgStatic.on")
      assert(Shell.Failure(-1) == st)
      val ctor = run(
        """class W(x: Option[Int]) { public: def get: Option[Int] = x }
          |def main(args: String[]): String = "" + new W(if 1 < 2 { other } else { tail }).get
          |""".stripMargin, "RawJoinArgCtor.on")
      assert(Shell.Failure(-1) == ctor)
    }
    it("a join still goes to Object, to a raw value and through a method of the class") {
      val r = run(
        """def show(o: Object): String = "" + o
          |val x = if 1 < 2 { other } else { tail }
          |val o: Object = x
          |val same: Option[Int] = if 1 > 2 { Option::some(4) } else { tail }
          |def main(args: String[]): String = show(x) + " " + o + " " + x.isDefined() + " " + same
          |""".stripMargin, "RawJoinOk.on")
      assert(Shell.Success("Some[value=str] Some[value=str] true None[]") == r)
    }
  }

  describe("the error is on the branch that does not fit (#3255)") {
    // Line 4 is the first line after the header; `other` is the bad branch.
    it("a call argument, a typed val and a lambda for a non-generic parameter") {
      assert(errors("def f(x: Option[Int]): Int = x.get()\nprintln(f(if 1 < 2 { other } else { tail }))\n") ==
        Seq("E0000@5:20"))
      assert(errors("val x: Option[Int] = if 1 < 2 { other } else { tail }\n") == Seq("E0000@4:31"))
      assert(errors(
        "def chk(f: () -> Option[Int]): Option[Int] = f()\nval c: Option[Int] = chk { if 1 < 2 { other } else { tail } }\n") ==
        Seq("E0000@5:37"))
    }
    it("an untyped local is reported where it is used") {
      assert(errors("val x = if 1 < 2 { other } else { tail }\nval c: Option[Int] = x\n") == Seq("E0000@5:22"))
    }
  }

  describe("sound programs keep compiling (#3255)") {
    it("a lambda method on a join of List[Int] and List[String] sees Object elements") {
      val r = run(
        """val li: List[Int] = [1, 2]
          |val ls: List[String] = ["a"]
          |val j = if 1 < 2 { li } else { ls }
          |def main(args: String[]): String =
          |  "" + j.map { x -> "" + x } + j.flatMap { x -> [x, x] } + j.mapIndexed { i, x -> "" + i + x }
          |""".stripMargin, "RawJoinMap.on")
      assert(Shell.Success("[1, 2][1, 1, 2, 2][01, 12]") == r)
    }
    it("an empty branch takes the parameter's type") {
      val r = run(
        """def f(x: Option[Int]): Int = x.get() + 1
          |def g(xs: List[Int]): Int = xs.size
          |val b = 1 < 2
          |def main(args: String[]): String =
          |  "" + f(if b { Option::some(1) } else { Option::none() }) + g(if b { [1, 2] } else { [] })
          |""".stripMargin, "RawJoinEmptyArg.on")
      assert(Shell.Success("22") == r)
    }
    it("an empty branch takes the other branch's type, with no target") {
      val r = run(
        """val b = 1 < 2
          |val x = if b { Option::some(1) } else { Option::none() }
          |val y: Option[Int] = x
          |val l = if b { [] } else { [1, 2] }
          |val l2: List[Int] = l
          |val m = if b { ["a": 1] } else { [:] }
          |val m2: java.util.Map[String, Int] = m
          |def main(args: String[]): String = "" + (y.get() + 1) + l2.size + m2
          |""".stripMargin, "RawJoinEmptyLocal.on")
      assert(Shell.Success("20{a=1}") == r)
    }
  }
}
