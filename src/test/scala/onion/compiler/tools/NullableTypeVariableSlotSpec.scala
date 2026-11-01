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
}
