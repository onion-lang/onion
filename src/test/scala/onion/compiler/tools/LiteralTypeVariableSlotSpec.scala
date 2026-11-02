package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * A list or map literal is a `List[T]` / `Map[K, T]` only when each element is a `T`
 * the way an assignment reads it (#2332). Before, `[u]` with an unrelated `u: U`
 * adopted the expected `List[T]` because `U` and `T` both erase to `Object`, and
 * `[t, u]` joined as `List[T]`; both compiled, then threw ClassCastException where
 * `T` was read back.
 */
class LiteralTypeVariableSlotSpec extends AbstractShellSpec {
  private def program(stmt: String) =
    s"""class S[T, U] {
       |  var t: T
       |  var u: U
       |  var xs: List[T]
       |  var xo: List[T?]
       |  var xss: List[List[T]]
       |  var m: Map[String, T]
       |  var k: Map[T, String]
       |  var os: List[Object]
       |public:
       |  def this(x: T, y: U) { t = x; u = y; xs = []; xo = []; xss = []; m = [:]; k = [:]; os = [] }
       |  def take(ys: List[T]): T { return ys[0] }
       |  def f(): void { $stmt }
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

  describe("a collection literal in a type-variable slot") {
    Seq(
      "xs = [u]"                          -> "[u] as a List[T]",
      "xs = [t, u]"                       -> "[t, u] as a List[T]",
      "xs = [\"s\"]"                      -> "a String literal element as a List[T]",
      "xo = [u, null]"                    -> "[u, null] as a List[T?]",
      "xss = [[u]]"                       -> "a nested [[u]] as a List[List[T]]",
      "val ys: List[T] = [u]"             -> "[u] as a declared List[T]",
      "take([u])"                         -> "[u] as a List[T] call argument",
      "take([t, u])"                      -> "[t, u] as a List[T] call argument",
      "m = [\"k\": u]"                    -> "a U value in a Map[String, T]",
      "k = [u: \"v\"]"                    -> "a U key in a Map[T, String]"
    ).foreach { case (stmt, what) =>
      it(s"rejects $what (E0000)") {
        assert(errorCodes(program(stmt)) == Seq(Some("E0000")))
      }
    }

    Seq(
      "xs = [t]", "xs = [t, t]", "xs = []", "xs = [(u as T)]", "xo = [t, null]", "xo = [null]",
      "xss = [[t], []]", "m = [\"k\": t]", "k = [t: \"v\"]", "take([t])",
      "os = [t, u]", "os = [t, \"s\", 1]", "val o: Object = [t, u]", "val zs = [t, u]"
    ).foreach { stmt =>
      it(s"still accepts `$stmt`") {
        assert(errorCodes(program(stmt)) == Seq.empty)
      }
    }
  }

  it("no longer compiles the issue's repro, which threw ClassCastException") {
    val src =
      """class S[T, U] {
        |  var ts: List[T]
        |  var u: U
        |public:
        |  def this(y: U) { ts = []; u = y }
        |  def f(): void { ts = [u] }
        |  def get(): T { return ts[0] }
        |}
        |def main(args: String[]): Int {
        |  val s = new S[String, Integer](1)
        |  s.f()
        |  val r: String = s.get()
        |  return r.length()
        |}
        |""".stripMargin
    assert(errorCodes(src) == Seq(Some("E0000")))
  }

  it("still runs generic and concrete literals") {
    val src =
      """class Box[T] {
        |  var items: List[T]
        |  var byName: Map[String, T]
        |public:
        |  def this(x: T) { items = [x, x]; byName = ["a": x] }
        |  def count(): Int { return items.size + byName.size }
        |}
        |class Up[A, B extends A] {
        |  var b: B
        |public:
        |  def this(y: B) { b = y }
        |  def asA(): List[A] { return [b] }
        |}
        |def main(args: String[]): Int {
        |  val xs: List[String?] = ["a", null]
        |  val os: List[Object] = ["a", 1]
        |  val ns: List[Number] = [1, 2.5]
        |  val mixed = [1, 2L]
        |  return new Box[String]("s").count() * 10 + new Up[Object, String]("q").asA().size +
        |    xs.size + os.size + ns.size + mixed.size
        |}
        |""".stripMargin
    assert(shell.run(src, "None", Array()) == Shell.Success(39))
  }
}
