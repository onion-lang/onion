package onion.compiler.tools

import onion.compiler.{CompilationOutcome, CompilerConfig, OnionCompiler, StreamInputSource}
import onion.tools.Shell

import java.io.StringReader

/**
 * `xs[i] = v` and `m[k] = v` check `v` (and `k`) the way `xs.set(i, v)` and
 * `m.put(k, v)` do (#2332). Before, an unrelated `U` compiled into a `List[T]`
 * or `Map[String, T]` through the indexed form, then failed with
 * ClassCastException where `T` was read back.
 */
class IndexedTypeVariableSlotSpec extends AbstractShellSpec {
  private def program(stmt: String) =
    s"""import { java.util.* }
       |class Box[E] {
       |  var e: E?
       |public:
       |  def this { e = null }
       |  def set(i: Int, v: E): void { e = v }
       |}
       |class S[T, U] {
       |  var t: T
       |  var u: U
       |  var xs: List[T]
       |  var xo: List[T?]
       |  var m: Map[String, T]
       |  var k: Map[T, String]
       |  var b: Box[T]
       |  var ys: List[String]
       |  var ns: List[Int]
       |  var os: List[Object]
       |  var arr: T[]
       |public:
       |  def this(x: T, y: U) {
       |    t = x; u = y
       |    xs = new ArrayList[T](); xo = new ArrayList[T?](); m = new HashMap[String, T]()
       |    k = new HashMap[T, String](); b = new Box[T]()
       |    ys = new ArrayList[String](); ns = new ArrayList[Int](); os = new ArrayList[Object]()
       |    arr = null
       |  }
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

  describe("an indexed assignment into a type-variable slot") {
    Seq(
      "xs[0] = u"                        -> "an unrelated U into a List[T]",
      "xo[0] = u"                        -> "an unrelated U into a List[T?]",
      "m[\"k\"] = u"                     -> "an unrelated U into a Map[String, T]",
      "k[u] = \"v\""                     -> "an unrelated U as a Map[T, String] key",
      "b[0] = u"                         -> "an unrelated U into a user class's set(Int, E)",
      "val o: Object = \"s\"; xs[0] = o" -> "an Object into a List[T]",
      "xs[0] = \"s\""                    -> "a String into a List[T]"
    ).foreach { case (stmt, what) =>
      it(s"rejects $what (E0000)") {
        assert(errorCodes(program(stmt)) == Seq(Some("E0000")))
      }
    }

    Seq(
      "xs[0] = t", "xo[0] = t", "xo[0] = null", "m[\"k\"] = t", "k[t] = \"v\"", "b[0] = t",
      "xs[0] = (u as T)", "ys[0] = \"a\"", "ns[0] = 5", "os[0] = \"s\"", "os[0] = 5",
      "arr[0] = t", "val i: Integer = 0; xs[i] = t"
    ).foreach { stmt =>
      it(s"still accepts `$stmt`") {
        assert(errorCodes(program(stmt)) == Seq.empty)
      }
    }
  }

  it("no longer compiles the issue's repro, which threw ClassCastException") {
    val src =
      """import { java.util.* }
        |class S[T, U] {
        |  val u: U
        |public:
        |  def this(y: U) { u = y }
        |  def idx(): List[T] { val xs: List[T] = new ArrayList[T](); xs.add(null); xs[0] = u; return xs }
        |}
        |def main(args: String[]): Int {
        |  val s: String = new S[String, Integer](1).idx()[0]
        |  return s.length()
        |}
        |""".stripMargin
    assert(errorCodes(src) == Seq(Some("E0000")))
  }

  it("still runs concrete indexed assignments") {
    val src =
      """import { java.util.* }
        |def main(args: String[]): Int {
        |  val xs: List[Int] = new ArrayList[Int]()
        |  xs.add(1); xs.add(2)
        |  xs[1] = 40
        |  val m: Map[String, Long] = new HashMap[String, Long]()
        |  m["a"] = 2L
        |  val os: List[Object] = new ArrayList[Object]()
        |  os.add(null)
        |  os[0] = 3
        |  return xs[0] + xs[1] + (m["a"] as Int) - (os[0] as Int)
        |}
        |""".stripMargin
    assert(shell.run(src, "None", Array()) == Shell.Success(40))
  }
}
