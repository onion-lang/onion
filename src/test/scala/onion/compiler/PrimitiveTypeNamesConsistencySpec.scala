package onion.compiler

import onion.compiler.typing.NameResolver
import org.scalatest.funspec.AnyFunSpec

import java.io.StringReader

/**
 * `PrimitiveTypeNames` (Onion's eight capitalized primitive keywords) used to be
 * listed twice: once in `typing.NameResolver` (class-not-found suggestions -- a
 * lowercase `int` reads as an ordinary unresolved reference type and gets "did you
 * mean `Int`?") and again in `parser.SyntaxHintClassifier` (the
 * `hint.primitive_dot_static` syntax hint for `Int.parseInt(...)`-style mistakes).
 * Nothing tied the two together -- the same drift risk already closed for
 * `DeriveMarkers` and `ShapeFormats`. This asserts both call sites now read from the
 * single shared `PrimitiveTypeNames.all`.
 */
class PrimitiveTypeNamesConsistencySpec extends AnyFunSpec {

  private def messages(src: String): String = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.map(_.message).mkString("\n")
      case _ => ""
    }
  }

  it("lists the eight capitalized primitive keywords") {
    assert(PrimitiveTypeNames.all == Seq("Int", "Long", "Short", "Byte", "Char", "Float", "Double", "Boolean"))
  }

  it("is the same list NameResolver suggests for an unresolved lowercase primitive") {
    assert(NameResolver.PrimitiveTypeNames == PrimitiveTypeNames.all)
  }

  it("fires the primitive_dot_static hint for every name in PrimitiveTypeNames.all") {
    PrimitiveTypeNames.all.foreach { name =>
      val msgs = messages(
        s"""
           |class Main {
           |public:
           |  static def main(args: String[]): void {
           |    IO::println($name.parseIt("x"))
           |  }
           |}
           |""".stripMargin
      )
      assert(msgs.contains(s"$name::parseIt"), s"expected a `$name::parseIt` hint for `$name`, got: $msgs")
    }
  }
}
