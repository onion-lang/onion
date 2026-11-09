package onion.compiler.tools

import java.io.{ByteArrayOutputStream, PrintStream, StringReader}
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/** The E0114 round-trip check runs a shape only when its effects are at most `mutate` (#2733, #2601). */
class ShapeBuildRunsOnlyPureSpec extends AnyFunSpec {
  private def shape(extra: String, printBody: String) =
    s"""record P(n: Int)
       |class Loud conforms Shape[P] {
       |public:
       |  def this {}
       |  def parse(text: String, origin: Origin): Outcome[P] {
       |    $extra
       |    return Outcome::ok(new P(Integer::parseInt(text)))
       |  }
       |  def canPrint(): Boolean = true
       |  def print(v: P): String {
       |    $printBody
       |    return "" + v.n()
       |  }
       |  def describe(): String = "loud"
       |}
       |IO::println("hi")
       |""".stripMargin

  /** Compiles, returning what the build wrote to stdout, the error codes and the warning codes. */
  private def build(src: String, timeoutMillis: Long = 10000L): (String, Seq[String], Seq[String]) = {
    val (out, errs, warns) = buildDetailed(src, timeoutMillis)
    (out, errs, warns.map(_._1))
  }

  /** As `build`, with each warning's code and message. */
  private def buildDetailed(src: String, timeoutMillis: Long = 10000L): (String, Seq[String], Seq[(String, String)]) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check,
      contractTimeoutMillis = timeoutMillis)
    val out = new ByteArrayOutputStream
    val saved = System.out
    val r = try {
      System.setOut(new PrintStream(out, true, "UTF-8"))
      new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "S.on")))
    } finally System.setOut(saved)
    (out.toString("UTF-8"), r.diagnostics.errors.flatMap(_.errorCode), r.diagnostics.warnings.map(w => (w.category.code, w.message)))
  }

  it("does not run a shape that prints to the console while compiling, and says so (W0023)") {
    val (out, errs, warns) = build(shape("", """IO::println("LEAK-print")"""))
    assert(!out.contains("LEAK"), out)
    assert(!errs.contains("E0114"))
    assert(warns.contains("W0023"), warns)
  }

  it("does not run a parse with an outside effect") {
    val (out, _, warns) = build(shape("""IO::println("LEAK-parse")""", ""))
    assert(!out.contains("LEAK"), out)
    assert(warns.contains("W0023"), warns)
  }

  it("still checks a shape with no outside effect (E0114 for one that does not round-trip)") {
    val broken = shape("", "").replace("""return "" + v.n()""", """return "" + (v.n() + 1)""")
    val (_, errs, warns) = build(broken)
    assert(errs.contains("E0114"))
    assert(!warns.contains("W0023"))
  }

  it("does not run a printable the shape inherits from a program interface (panel block at e8fef6a)") {
    val src =
      """record P(n: Int)
        |interface Mixin {
        |  def printable(v: P): Boolean {
        |    IO::println("LEAK-mixin")
        |    return true
        |  }
        |}
        |class Loud conforms Mixin, Shape[P] {
        |public:
        |  def this {}
        |  def parse(text: String, origin: Origin): Outcome[P] = Outcome::ok(new P(Integer::parseInt(text)))
        |  def canPrint(): Boolean = true
        |  def print(v: P): String = "" + v.n()
        |  def describe(): String = "loud"
        |}
        |IO::println("hi")
        |""".stripMargin
    val (out, _, warns) = build(src)
    assert(!out.contains("LEAK"), out)
    assert(warns.contains("W0023"), warns)
  }

  it("gives up on a round-trip that does not finish, and says so (W0023), instead of hanging the build") {
    val (_, errs, warns) = build(shape("var i = 0\n    while true { i = i + 1 }", ""), timeoutMillis = 300L)
    assert(warns.contains("W0023"), warns)
    assert(!errs.contains("E0114"))
  }

  it("names where the effect is in the build's language (no English in the ja text)") {
    val src = shape("", "").replace("def this {}", "def this { IO::println(\"LEAK-ctor\") }")
    val (out, _, warns) = buildDetailed(src)
    assert(!out.contains("LEAK"), out)
    val msg = warns.collectFirst { case ("W0023", m) => m }.getOrElse(fail(s"no W0023 in $warns"))
    if (java.util.Locale.getDefault.getLanguage == "ja")
      Seq("its constructor", "making a sample", "constructor").foreach(p => assert(!msg.contains(p), msg))
    else assert(msg.contains("constructor"), msg)
  }

  it("points at the value type, not the shape, when making a sample has the effect") {
    val src = shape("", "").replace("record P(n: Int)",
      "class P {\n  val n0: Int\npublic:\n  def this(n: Int) { IO::println(\"LEAK-sample\"); n0 = n }\n  def n(): Int = n0\n}")
    val (out, _, warns) = buildDetailed(src)
    assert(!out.contains("LEAK"), out)
    val msg = warns.collectFirst { case ("W0023", m) => m }.getOrElse(fail(s"no W0023 in $warns"))
    if (java.util.Locale.getDefault.getLanguage == "ja") assert(msg.contains("`P` の外"), msg)
    else assert(msg.contains("out of `P`"), msg)
  }
}
