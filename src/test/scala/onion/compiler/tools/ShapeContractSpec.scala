package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * The contract of `onion.Shape` (RFC #2557, design 5.1): `parse(print(v)) == Ok(v)` for every value
 * the shape can print. The domain is stated by overriding `printable`, once, in the shape.
 */
class ShapeContractSpec extends AnyFunSpec {
  private def codes(src: String): Seq[String] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "S.on"))) match {
      case CompilationOutcome.Failure(errs) => errs.flatMap(_.errorCode)
      case _                                => Nil
    }
  }

  /** A fixed-width shape: `name` is padded to 10 columns and not cut, so a longer name cannot round-trip. */
  private def fixedWidth(printable: String) =
    s"""record Person(name: String, amount: Int)
       |
       |class FixedWidth conforms Shape[Person] {
       |public:
       |  def this {}
       |  def parse(text: String, origin: Origin): Outcome[Person] {
       |    if text == null || text.length() != 16 {
       |      return Outcome::bad(Defect::at(origin, "", "a 16-column record", "other"))
       |    }
       |    try {
       |      return Outcome::ok(new Person(text.substring(0, 10).trim(), Integer::parseInt(text.substring(10, 16))))
       |    } catch e: NumberFormatException {
       |      return Outcome::bad(Defect::at(origin, "amount", "Int", text.substring(10, 16)))
       |    }
       |  }
       |  def canPrint(): Boolean = true
       |  def print(v: Person): String {
       |    val name = new StringBuilder(v.name())
       |    while name.length() < 10 { name.append(" ") }
       |    var amt = "" + v.amount()
       |    while amt.length() < 6 { amt = "0" + amt }
       |    return name.toString() + amt
       |  }
       |  def describe(): String = "fixed-width Person"
       |$printable
       |}
       |""".stripMargin

  it("breaks the build when a shape does not round-trip a value it claims to print (E0114)") {
    assert(codes(fixedWidth("")).contains("E0114"))
  }

  it("passes once the shape states the domain it can print") {
    val domain =
      """  def printable(v: Person): Boolean =
        |    v.name().length() <= 10 && v.name() == v.name().trim() && v.amount() >= 0 && v.amount() <= 999999""".stripMargin
    assert(!codes(fixedWidth(domain)).contains("E0114"))
  }

  it("owes nothing when the shape cannot print") {
    val src =
      """record Person(name: String, amount: Int)
        |class ReadOnly conforms Shape[Person] {
        |public:
        |  def this {}
        |  def parse(text: String, origin: Origin): Outcome[Person] = Outcome::ok(new Person(text, 0))
        |  def canPrint(): Boolean = false
        |  def print(v: Person): String { throw new UnsupportedOperationException("read-only") }
        |  def describe(): String = "read-only"
        |}
        |""".stripMargin
    assert(!codes(src).contains("E0114"))
  }
}
