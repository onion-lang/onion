package onion.compiler.tools

import java.io.StringReader
import onion.compiler.{CompilationOutcome, CompileError, CompilerConfig, ContractMode, OnionCompiler, StreamInputSource}
import org.scalatest.funspec.AnyFunSpec

/**
 * #2605: E0106 and E0077 name the call that has the effect. An instance method is named
 * `Owner.method`, as a call on a value reads; `Owner::method` is how Onion writes a static
 * call, so it is kept for static methods only. Before, `xs.add(1)` was named
 * `java.util.List::add`. Assertions use codes and identifiers, the same in every locale.
 */
class EffectCallWordingSpec extends AnyFunSpec {

  private def errors(source: String): Seq[CompileError] = {
    val config = new CompilerConfig(Seq("."), null, "UTF-8", "", 10, runContracts = false, contracts = ContractMode.Check)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(source), "C.on"))) match {
      case CompilationOutcome.Failure(es) => es
      case other                          => fail(s"expected a compile failure, got $other")
    }
  }

  private def messagesOf(source: String, code: String): String = {
    val errs = errors(source)
    val found = errs.filter(_.errorCode.contains(code))
    assert(found.nonEmpty, s"expected $code, got:\n${errs.map(_.message).mkString("\n")}")
    found.map(_.message).mkString("\n")
  }

  describe("E0106 (a contract that is not pure)") {
    it("names an instance call as Owner.method, not Owner::method") {
      val msg = messagesOf(
        """class K {
          |public:
          |  static def f(xs: List[Int]): Int ensures xs.add(1) { return 1 }
          |}""".stripMargin, "E0106")
      assert(msg.contains("java.util.List.add"), msg)
      assert(!msg.contains("List::add"), msg)
    }

    it("names a program class's instance method as Owner.method") {
      val msg = messagesOf(
        """class Counter {
          |  var n: Int = 0
          |public:
          |  def bump(): Int { n = n + 1; return n }
          |}
          |class K {
          |public:
          |  static def f(c: Counter): Int requires c.bump() > 0 { return 1 }
          |}""".stripMargin, "E0106")
      assert(msg.contains("Counter.bump"), msg)
      assert(!msg.contains("Counter::bump"), msg)
    }

    it("keeps Owner::method for a static call") {
      val msg = messagesOf(
        """class K {
          |public:
          |  static def f(n: Int): Int requires Rand::nextInt(10) >= 0 { return n }
          |}""".stripMargin, "E0106")
      assert(msg.contains("Rand::nextInt"), msg)
    }
  }

  describe("E0077 (an undeclared effect in a tool)") {
    it("names an instance call as Owner.method") {
      val msg = messagesOf(
        """class Logger {
          |public:
          |  def log(s: String): void { IO::println(s) }
          |}
          |tool hello(name: String): void
          |  requires { read }
          |{
          |  new Logger().log(name)
          |}
          |""".stripMargin, "E0077")
      assert(msg.contains("Logger.log"), msg)
      assert(!msg.contains("Logger::log"), msg)
    }

    it("keeps Owner::method for a static call") {
      val msg = messagesOf(
        """class Loader {
          |public:
          |  static def load(p: String): String { return Files::readText(p) }
          |}
          |tool answer(p: String): String
          |  requires { console }
          |{
          |  return Loader::load(p)
          |}
          |""".stripMargin, "E0077")
      assert(msg.contains("Loader::load"), msg)
    }
  }
}
