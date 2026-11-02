package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import onion.compiler.parser.{JJOnionParser, OnionParser}
import org.scalatest.funspec.AnyFunSpec

/**
 * Every place that parses a contract clause carries it to typing (RFC #2557, issue #2577):
 * an extension method, an enum body (plain or `case`), an interface method and an
 * `abstract def`. With contracts off each clause is E0105; with `check` a bad clause gets
 * the same error a concrete class method's clause gets, never silence.
 *
 * Assertions use error codes, which are the same in every locale.
 */
class ContractPlacementSpec extends AnyFunSpec {

  private def compile(source: String, mode: ContractMode): Seq[CompileError] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, checkLaws = false, contracts = mode)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "P.on"))) match {
      case CompilationOutcome.Failure(errs) => errs
      case _                                => Nil
    }
  }

  private def codes(source: String, mode: ContractMode = ContractMode.Check): Seq[String] =
    compile(source, mode).flatMap(_.errorCode)

  private val extensionUndefined =
    """extension String {
      |  def twice(): String requires noSuchName > 1 = this + this
      |}
      |println("ab".twice())""".stripMargin

  private val extensionResultUndefined =
    """extension String {
      |  def twice(): String ensures result == nope = this + this
      |}
      |println("ab".twice())""".stripMargin

  private val extensionGood =
    """extension String {
      |  def times(n: Int): String requires n >= 0 ensures result.length() == this.length() * n {
      |    val sb = new StringBuilder()
      |    foreach i in 1..n { sb.append(this) }
      |    return sb.toString()
      |  }
      |}
      |println("ab".times(2))""".stripMargin

  private val enumUndefined =
    """enum Color {
      |  RED, GREEN
      |public:
      |  invariant noSuchThing > 0
      |}
      |println(Color::RED)""".stripMargin

  private val adtEnumUndefined =
    """enum Shape {
      |  case Circle(radius: Double)
      |  case Origin
      |public:
      |  invariant noSuchThing > 0
      |}
      |println(1)""".stripMargin

  private val interfaceUndefined =
    """interface I {
      |  def f(x: Int): Int requires x > 0 && undefinedName
      |}
      |println(1)""".stripMargin

  private val interfaceImpure =
    """interface I {
      |  def f(x: Int): Int requires IO::readLine() != null
      |}
      |println(1)""".stripMargin

  private val interfaceGood =
    """interface I {
      |  def f(x: Int): Int requires x > 0 ensures result >= x
      |}
      |println(1)""".stripMargin

  private val abstractUndefined =
    """abstract class B {
      |public:
      |  abstract def g(x: Int): Int requires undefinedName2
      |}
      |println(1)""".stripMargin

  private val abstractResultInVoid =
    """abstract class B {
      |public:
      |  abstract def g(x: Int): void ensures result > 0
      |}
      |println(1)""".stripMargin

  private val abstractGood =
    """abstract class B {
      |public:
      |  abstract def g(x: Int): Int requires x > 0 ensures result > old(x)
      |}
      |println(1)""".stripMargin

  private val withClauses = Seq(
    "extension (requires)" -> extensionUndefined,
    "extension (ensures)"  -> extensionResultUndefined,
    "extension (good)"     -> extensionGood,
    "enum"                 -> enumUndefined,
    "case enum"            -> adtEnumUndefined,
    "interface"            -> interfaceUndefined,
    "abstract method"      -> abstractUndefined
  )

  describe("with contracts off, a clause in any place is E0105") {
    withClauses.foreach { case (where, src) =>
      it(s"in: $where") {
        assert(codes(src, ContractMode.Off).contains("E0105"), codes(src, ContractMode.Off).toString)
      }
    }
  }

  describe("with --contracts check, a clause in any place is typed") {
    it("rejects an undefined name in an extension method's requires") {
      assert(codes(extensionUndefined).contains("E0002"), codes(extensionUndefined).toString)
    }
    it("rejects an undefined name in an extension method's ensures") {
      assert(codes(extensionResultUndefined).contains("E0002"), codes(extensionResultUndefined).toString)
    }
    it("accepts an extension method's clauses that see the parameters, this and result") {
      assert(codes(extensionGood).isEmpty, codes(extensionGood).toString)
    }
    it("rejects an undefined name in an enum's invariant") {
      assert(codes(enumUndefined).contains("E0002"), codes(enumUndefined).toString)
    }
    it("rejects an undefined name in a case enum's invariant") {
      assert(codes(adtEnumUndefined).contains("E0002"), codes(adtEnumUndefined).toString)
    }
    it("rejects an undefined name on an interface method") {
      assert(codes(interfaceUndefined).contains("E0002"), codes(interfaceUndefined).toString)
    }
    it("rejects an impure call on an interface method, as on a concrete one") {
      assert(codes(interfaceImpure).contains("E0106"), codes(interfaceImpure).toString)
    }
    it("accepts an interface method's clauses over its parameters and result") {
      assert(codes(interfaceGood).isEmpty, codes(interfaceGood).toString)
    }
    it("rejects an undefined name on an abstract method") {
      assert(codes(abstractUndefined).contains("E0002"), codes(abstractUndefined).toString)
    }
    it("rejects result in the ensures of an abstract void method") {
      assert(codes(abstractResultInVoid).nonEmpty, codes(abstractResultInVoid).toString)
    }
    it("accepts an abstract method's clauses over its parameters, old and result") {
      assert(codes(abstractGood).isEmpty, codes(abstractGood).toString)
    }
  }

  describe("both parsers read the clauses the same way") {
    (withClauses ++ Seq("interface (good)" -> interfaceGood, "abstract (good)" -> abstractGood)).foreach { case (where, src) =>
      it(s"in: $where") {
        val fast = OnionParser.parse(src)
        val jj = new JJOnionParser(new StringReader(src)).unit()
        assert(fast == jj)
      }
    }
  }
}
