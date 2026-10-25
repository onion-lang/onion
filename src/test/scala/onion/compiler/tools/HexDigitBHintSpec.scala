package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource}
import onion.compiler.parser.{JJOnionParser, OnionParser}
import onion.compiler.toolbox.Message
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * Migration aid for #2324: `B` at the end of a hex literal is a digit now, not a Byte
 * suffix, so `val x: Byte = 0xFFB` (the Byte -1 before) is an Int that no longer fits.
 * Where such a literal fails in a Byte slot, the error says why and how to write a Byte.
 * The expected text comes from `Message(...)`, so this holds in both locales.
 */
class HexDigitBHintSpec extends AnyFunSpec {

  private def compile(code: String) =
    new OnionCompiler(CompilerConfig(Seq("."), null, "UTF-8", "", 10)).compileDetailed(
      Seq(new StreamInputSource(() => new StringReader(code), "Test.on"))
    )

  private val hint = Message("suggestion.hexDigitB")

  describe("a hex literal ending in B that fails in a Byte slot") {
    val hinted = Seq(
      "val x: Byte = 0xFFB",
      "val bs = new Byte[2]\nbs[0] = 0x80B",
      "var x: Byte = 1B\nx = -0x80B",
      "def g(): Byte = 0xFFB",
      "def f(b: Byte): Int = b\nval y = f(0xFFB)",
      "class K {\npublic:\n  def m(s: String, b: Byte): Int = b\n}\nval y = new K().m(\"a\", 0x1FFB)",
      "class K {\npublic:\n  static def m(b: Byte): Int = b\n}\nval y = K::m(0xFFB)"
    )
    hinted.foreach { code =>
      it(s"says that B is a hex digit: `${code.replace("\n", "; ")}`") {
        val errors = compile(code + "\n").allErrors.map(_.message)
        assert(errors.nonEmpty)
        assert(errors.exists(_.contains(hint)), s"expected the hex-digit hint, got: $errors")
      }
    }
  }

  describe("an error that does not involve a hex literal ending in B") {
    val unhinted = Seq(
      "val x: Byte = 0xFF",
      "val x: Byte = 4091",
      "def f(b: Byte): Int = b\nval y = f(0xFF)",
      "def f(s: String): Int = 0\nval y = f(0xAB)",
      "val x: String = 0xAB"
    )
    unhinted.foreach { code =>
      it(s"gets no hex-digit hint: `${code.replace("\n", "; ")}`") {
        val errors = compile(code + "\n").allErrors.map(_.message)
        assert(errors.nonEmpty)
        assert(!errors.exists(_.contains(hint)), s"unexpected hex-digit hint in: $errors")
      }
    }
  }

  it("leaves a hex literal ending in B that fits a Byte slot compiling, as in Java") {
    val result = compile("val bs = new Byte[1]\nbs[0] = 0x1B\n")
    assert(!result.hasErrors, s"unexpected errors: ${result.allErrors.map(_.message)}")
  }

  it("gives both parsers the same AST for a hex literal ending in B") {
    Seq("val x: Int = 0xFFB\n", "val x = -0xAB\n", "val x = 0xab\n").foreach { src =>
      val fast = OnionParser.parse(src)
      assert(fast == new JJOnionParser(new StringReader(src)).unit(), src)
    }
  }

  it("no longer offers 0xFF as a Byte spelling in the Byte range message") {
    assert(!Message("error.parsing.byte_literal_out_of_range").contains("0xFF"))
  }
}
