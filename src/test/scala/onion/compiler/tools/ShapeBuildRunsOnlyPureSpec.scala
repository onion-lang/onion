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
  private def build(src: String): (String, Seq[String], Seq[String]) = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val out = new ByteArrayOutputStream
    val saved = System.out
    val r = try {
      System.setOut(new PrintStream(out, true, "UTF-8"))
      new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "S.on")))
    } finally System.setOut(saved)
    (out.toString("UTF-8"), r.diagnostics.errors.flatMap(_.errorCode), r.diagnostics.warnings.map(_.category.code))
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
}
