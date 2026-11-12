package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/** Framing rule 1 (RFC #2557, design 3.2): a clause names only value-typed fields. */
class ContractFramingSpec extends AnyFunSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, runContracts = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "C.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode)
      case _                                => Nil
    }
  }

  /** Where each E0110 points, as `line:column` (#2605, item 1: it used to have no location). */
  private def e0110At(src: String): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, runContracts = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "C.on"))) match {
      case CompilationOutcome.Failure(errs) =>
        errs.filter(_.errorCode.contains("E0110")).map(e => Option(e.location).fold("none")(l => s"${l.line}:${l.column}"))
      case _ => Nil
    }
  }

  it("accepts an Int field") {
    assert(!codes("class A {\n  invariant n >= 0\npublic:\n  var n: Int\n  def this { n = 0 }\n}").contains("E0110"))
  }
  it("accepts a String field") {
    assert(!codes("class A {\n  invariant s != null\npublic:\n  var s: String\n  def this { s = \"\" }\n}").contains("E0110"))
  }
  it("rejects a Java superclass other than Object (rule 4)") {
    val src = "import { java.util.* }\nclass A extends ArrayList {\n  invariant n >= 0\npublic:\n  var n: Int\n  def this { n = 0 }\n}"
    assert(codes(src).contains("E0111"))
  }
  it("accepts an Onion superclass") {
    val src = "class B {\n}\nclass A extends B {\n  invariant n >= 0\npublic:\n  var n: Int\n  def this { n = 0 }\n}"
    assert(!codes(src).contains("E0111"))
  }
  it("rejects a List field") {
    val src = "import { java.util.* }\nclass A {\n  invariant items != null\npublic:\n  var items: List\n  def this { items = new ArrayList() }\n}"
    assert(codes(src).contains("E0110"))
  }
  it("points E0110 at the field an invariant names (#2605)") {
    val src = "class Bag {\n  val xs: List[String]\n  invariant xs.size >= 0\npublic:\n  def this { xs = [] }\n}"
    assert(e0110At(src) == Seq("3:13"))
  }
  it("points E0110 at `this.field` in a history (#2605)") {
    val src = "class Bag {\n  var xs: List[String]\n  var n: Int\n  history n >= old(n) && this.xs != null\npublic:\n  def this { xs = []; n = 0 }\n}"
    assert(e0110At(src).size == 1 && e0110At(src).head.startsWith("4:"))
  }
}
