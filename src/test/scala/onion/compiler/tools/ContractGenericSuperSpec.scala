package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilerConfig, ContractMode, OnionCompiler, StreamInputSource, WarningLevel}
import org.scalatest.funspec.AnyFunSpec

/**
 * A generic Onion supertype is an Onion supertype (#2888). `class Up extends Base[String]` with a
 * `history` used to be E0111 ("extends the Java class `Base`"), and the contract checks that walk
 * a hierarchy stopped at `Base[String]` and `conforms I[T]`, so they treated the subclass as if it
 * had no parent. Each case below is checked against the same program without type arguments.
 */
class ContractGenericSuperSpec extends AnyFunSpec {
  private final case class Outcome(errors: Seq[String], warnings: Seq[String])

  private def compile(src: String): Outcome = {
    val config = CompilerConfig(Seq("."), null, "UTF-8", "", 10, warningLevel = WarningLevel.On, contracts = ContractMode.Check)
    val r = new OnionCompiler(config).compileDetailed(Seq(new StreamInputSource(() => new StringReader(src), "G.on")))
    Outcome(r.diagnostics.errors.flatMap(_.errorCode), r.diagnostics.warnings.map(_.category.code))
  }

  /** The program as written, and with `Name` made generic and used as `Name[String]`. */
  private def plainAndGeneric(src: String, name: String): Seq[String] =
    Seq(src, src.replace(s"extends $name {", s"extends $name[String] {")
      .replace(s"conforms $name {", s"conforms $name[String] {")
      .replace(s"class $name {", s"class $name[T] {").replace(s"interface $name {", s"interface $name[T] {"))

  private def base(extra: String = "") =
    s"""class Base {
       |protected:
       |  var n: Int
       |public:
       |  def this { n = 10 }
       |  def get(): Int = n
       |  $extra
       |}
       |class Up extends Base {
       |  history n >= old(n)
       |public:
       |  def inc(): void { if n < 1000 { n = n + 1 } }
       |}
       |val u = new Up(); u.inc(); println(u.get())
       |""".stripMargin

  it("accepts a history in a subclass of a generic Onion class (the issue's repro)") {
    val Seq(plain, generic) = plainAndGeneric(base(), "Base")
    assert(compile(plain).errors.isEmpty)
    assert(compile(generic).errors.isEmpty, compile(generic).toString)
  }

  it("still rejects a generic Java superclass with E0111") {
    val src = "import { java.util.* }\nclass A extends ArrayList[String] {\n  invariant n >= 0\npublic:\n  var n: Int\n  def this { n = 0 }\n}"
    assert(compile(src).errors.contains("E0111"))
  }

  it("checks a generic base's inherited mutator against the subclass's history (E0109)") {
    for (src <- plainAndGeneric(base("def dec(): void { n = n - 1 }"), "Base"))
      assert(compile(src).errors.contains("E0109"), src)
  }

  it("warns W0025 when an override breaks the ensures of a generic base") {
    val src =
      """class Base {
        |public:
        |  def this {}
        |  def f(x: Int): Int ensures result >= 0 { return x * x }
        |}
        |class Up extends Base {
        |public:
        |  def this {}
        |  override def f(x: Int): Int { return x - 100 }
        |}
        |""".stripMargin
    for (s <- plainAndGeneric(src, "Base"))
      assert(compile(s).warnings.contains("W0025"), s)
  }

  it("sees a history declared on a generic interface (W0024)") {
    val src =
      """interface Counter {
        |  history count() >= old(count())
        |  def count(): Int
        |}
        |class C conforms Counter {
        |  var n: Int
        |public:
        |  def this { n = 0 }
        |  def count(): Int = n
        |  def dec(): void { n = n - 1 }
        |}
        |""".stripMargin
    for (s <- plainAndGeneric(src, "Counter"))
      assert(compile(s).warnings.contains("W0024"), s)
  }
}
