package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * A `T?` slot takes what a `T` slot takes, plus `null` and an `S?` whose `S`
 * fits `T` (#2332). Before, any reference compiled into a `T?` field, and the
 * value then failed with ClassCastException where `T` was read back.
 */
class NullableTypeVariableSlotSpec extends AbstractShellSpec {
  private def program(stmt: String) =
    s"""import { java.util.* }
       |class S[T, U] {
       |  var t: T
       |  var o: T?
       |  var u: U
       |  var uo: U?
       |  var lo: List[T?]
       |public:
       |  def this(x: T, y: U) { t = x; o = null; u = y; uo = null; lo = new ArrayList[T?]() }
       |  def f(): void { $stmt }
       |  def g(): T? { return o }
       |}
       |def main(args: String[]): Int { return 0 }
       |""".stripMargin

  private def errors(source: String): Seq[onion.compiler.CompileError] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(source), "S.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.toSeq
      case _                                => Seq.empty
    }
  }

  private def errorCodes(source: String): Seq[Option[String]] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    val inputs = Seq(new StreamInputSource(() => new StringReader(source), "S.on"))
    new OnionCompiler(config).compile(inputs) match {
      case CompilationOutcome.Failure(errs) => errs.map(_.errorCode)
      case _                                => Seq.empty
    }
  }

  describe("assigning into a T? field") {
    Seq(
      "o = u"                                                     -> "an unrelated U",
      "o = uo"                                                    -> "an unrelated U?",
      "o = \"s\""                                                 -> "a String",
      "val x: Object = \"s\"; o = x"                              -> "an Object",
      "val l2: List[U?] = new ArrayList[U?](); lo = l2"           -> "a List[U?] into a List[T?]"
    ).foreach { case (stmt, what) =>
      it(s"rejects $what (E0000)") {
        assert(errorCodes(program(stmt)) == Seq(Some("E0000")))
      }
    }

    Seq(
      "o = t", "o = o", "o = null", "val x: T? = t; o = x",
      "val l2: List[T?] = new ArrayList[T?](); lo = l2",
      "val l2: List[T] = new ArrayList[T](); lo = l2",
      "o = (u as T)"
    ).foreach { stmt =>
      it(s"still accepts `$stmt`") {
        assert(errorCodes(program(stmt)) == Seq.empty)
      }
    }
  }

  it("no longer compiles the issue's repro, which threw ClassCastException") {
    val src =
      """class S[T, U] {
        |  var t: T?
        |  var u: U
        |public:
        |  def this(y: U) { t = null; u = y }
        |  def f(): void { t = u }
        |  def get(): T? { return t }
        |}
        |def main(args: String[]): Int {
        |  val s = new S[String, Integer](1)
        |  s.f()
        |  val r: String? = s.get()
        |  IO::println(r?.length())
        |  return 0
        |}
        |""".stripMargin
    assert(Shell.Failure(-1) == shell.run(src, "None", Array()))
    assert(errorCodes(src) == Seq(Some("E0000")))
  }

  it("still runs a T? field that holds a T or null") {
    val src =
      """class Cell[T] {
        |  var v: T?
        |public:
        |  def this { v = null }
        |  def put(x: T): void { v = x }
        |  def get(): T? = v
        |}
        |def main(args: String[]): Int {
        |  val c = new Cell[String]()
        |  val before = c.get() ?: "none"
        |  c.put("hi")
        |  IO::println(before + " " + (c.get() ?: "none"))
        |  return 0
        |}
        |""".stripMargin
    assert(Shell.Success(0) == shell.run(src, "None", Array()))
  }

  it("gives a T? slot the same hints as a T slot, not the lost-type-argument hint") {
    // `U` is a class parameter, not a type argument a cast dropped (#2332 review).
    val errs = errors(program("o = u"))
    assert(errs.map(_.errorCode) == Seq(Some("E0000")))
    assert(!errs.head.message.contains("Generic.Case"))
  }

  it("names the variable, not `T?`, in the shadowed-type-parameter hint") {
    val src =
      """class K[T] {
        |  var o: T?
        |public:
        |  def this { o = null }
        |  def m[T](a: T): void { o = a }
        |}
        |def main(args: String[]): Int { return 0 }
        |""".stripMargin
    val errs = errors(src)
    assert(errs.map(_.errorCode) == Seq(Some("E0000")))
    assert(errs.head.message.contains("`[T]`"))
    assert(!errs.head.message.contains("[T?]"))
  }

  describe("a constructor argument into a T? parameter") {
    def ctorProgram(arg: String) =
      s"""class S[T, U] {
         |  var t: T?
         |  var u: U
         |public:
         |  def this(x: T?, y: U) { t = x; u = y }
         |  def f(uo: U?, tt: T): S[T, U] { return new S[T, U]($arg, u) }
         |  def get(): T? { return t }
         |}
         |def main(args: String[]): Int { return 0 }
         |""".stripMargin

    Seq("u" -> "an unrelated U", "uo" -> "an unrelated U?").foreach { case (arg, what) =>
      it(s"rejects $what (E0021)") {
        assert(errorCodes(ctorProgram(arg)) == Seq(Some("E0021")))
      }
    }

    Seq("tt", "t", "null", "(u as T)").foreach { arg =>
      it(s"still accepts `$arg`") {
        assert(errorCodes(ctorProgram(arg)) == Seq.empty)
      }
    }

    it("no longer compiles the panel's repro, which threw ClassCastException") {
      val src =
        """class S[T, U] {
          |  var t: T?
          |  var u: U
          |public:
          |  def this(x: T?, y: U) { t = x; u = y }
          |  def f(): S[T, U] { return new S[T, U](u, u) }
          |  def get(): T? { return t }
          |}
          |def main(args: String[]): Int {
          |  val s = new S[String, Integer](null, 1)
          |  val r: String? = s.f().get()
          |  IO::println(r?.length())
          |  return 0
          |}
          |""".stripMargin
      assert(errorCodes(src) == Seq(Some("E0021")))
    }

    it("still runs a T? constructor parameter given a T or null") {
      val src =
        """class Cell[T] {
          |  var v: T?
          |public:
          |  def this(x: T?) { v = x }
          |  def copy(): Cell[T] = new Cell[T](v)
          |  def get(): T? = v
          |}
          |def main(args: String[]): Int {
          |  val a = new Cell[String](null)
          |  val b = new Cell[String]("hi").copy()
          |  IO::println((a.get() ?: "none") + " " + (b.get() ?: "none"))
          |  return 0
          |}
          |""".stripMargin
      assert(Shell.Success(0) == shell.run(src, "None", Array()))
    }
  }
}
