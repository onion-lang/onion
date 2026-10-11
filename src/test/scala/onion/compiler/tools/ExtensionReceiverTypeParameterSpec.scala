package onion.compiler.tools

import onion.compiler.{CompilerConfig, OnionCompiler, StreamInputSource, CompilationOutcome}
import onion.tools.Shell

import java.io.StringReader

/**
 * `extension List[T] { ... }` mirrors `class Box[T]`/`record R[T]` syntax as if
 * `T` were a fresh type parameter for the extension, but `extension_decl` (the
 * grammar production) has no leading `[T]` parameter list at all -- `T` is just
 * an ordinary, here-unresolvable type reference. The receiver as a whole then
 * failed to resolve with only the generic `[E0003] type List[T] is not found.
 * Check spelling or add an import.`, which is actively misleading: `List` is a
 * perfectly well-known type, so "check spelling" sends the reader looking in
 * the wrong place. `extension List { ... }` (raw, erasure-based, applying to
 * every instantiation) and `extension List[Int] { ... }` (one concrete
 * instantiation) are the two real options (`ExtensionGenericParamNameSpec`
 * already covers the latter working). This adds a dedicated hint naming both,
 * found while probing the compiler for gaps.
 */
class ExtensionReceiverTypeParameterSpec extends AbstractShellSpec {
  private def newConfig: CompilerConfig = CompilerConfig(Seq("."), null, "UTF-8", "", 10)

  private def compile(code: String) =
    new OnionCompiler(newConfig).compile(Seq(new StreamInputSource(() => new StringReader(code), "Test.on")))

  private val src =
    """
      |extension List[T] {
      |  def second: Object? = if this.size > 1 { this[1] } else { null }
      |}
      |
      |class Main {
      |public:
      |  static def main(args: String[]): void { }
      |}
      |""".stripMargin

  describe("extension List[T] { ... } (T meant as a fresh type parameter)") {
    it("fails to compile") {
      val result = shell.run(src, "ExtTypeParam.on", Array())
      assert(Shell.Failure(-1) == result)
    }

    it("still reports E0003 (the receiver genuinely doesn't resolve)") {
      compile(src) match {
        case CompilationOutcome.Failure(errors) =>
          val codes = errors.flatMap(_.errorCode)
          assert(codes.contains("E0003"), s"expected E0003 in $codes")
        case other => fail(s"expected a compile failure, got: $other")
      }
    }

    it("hints at `extension List { ... }` / `extension List[Int] { ... }` instead of just 'check spelling'") {
      compile(src) match {
        case CompilationOutcome.Failure(errors) =>
          val messages = errors.map(_.message)
          assert(
            messages.exists(_.contains("extension List")),
            s"expected a hint naming `extension List { ... }`/`extension List[Int] { ... }`, got: $messages"
          )
        case other => fail(s"expected a compile failure, got: $other")
      }
    }
  }

  describe("extension List[Int] { ... } (a real concrete instantiation, no regression)") {
    it("still compiles fine") {
      val result = shell.run(
        """
          |extension List[Int] {
          |  def intSum(): Int {
          |    return this.fold(0) { acc, x -> (acc as Int) + (x as Int) }
          |  }
          |}
          |
          |class Main {
          |public:
          |  static def main(args: String[]): String {
          |    return "" + [1, 2, 3].intSum()
          |  }
          |}
          |""".stripMargin,
        "ExtConcrete.on",
        Array()
      )
      assert(Shell.Success("6") == result)
    }
  }
}
