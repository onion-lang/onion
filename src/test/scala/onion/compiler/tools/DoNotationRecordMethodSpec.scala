package onion.compiler.tools

import onion.compiler.{OnionCompiler, CompilerConfig, StreamInputSource, CompilationOutcome}
import onion.tools.Shell
import java.io.StringReader

/**
 * `rewriteRecordDeclaration` synthesized derived methods (from/data/json/yaml/shape/law/
 * example) but never rewrote the record's own user-written `sections`, so body-level
 * rewrites - chiefly do-notation desugaring - never ran on a record method. A raw,
 * unrewritten `do[Option] { ... }` then reached typing directly, where a dead dispatch
 * branch silently returned no diagnostic; the resulting `None` was misread by the
 * local-variable codegen path as "an error was already reported", so the entire
 * initializer was dropped as a no-op rather than emitted - with no compiler error at all,
 * and a `java.lang.VerifyError: Bad local variable type` at class-load time instead.
 *
 * Fixed by having `rewriteRecordDeclaration` also rewrite `declaration.sections`, mirroring
 * `rewriteClassDeclaration`. A class already rejects `do[Option] { s <- v(); ret s }` over a
 * nullable `v(): String?` receiver with E0070 (dereferencing a nullable value directly,
 * since the desugared `v().map(...)` is a plain method call on a `String?`); a record method
 * must report the exact same E0070, not silently corrupt its bytecode.
 */
class DoNotationRecordMethodSpec extends AbstractShellSpec {
  private def errorCodes(src: String): Seq[String] = {
    val config = new CompilerConfig(List("."), null, "UTF-8", "", 10)
    new OnionCompiler(config).compile(Seq(new StreamInputSource(() => new StringReader(src), "test.on"))) match {
      case CompilationOutcome.Failure(errors) => errors.flatMap(_.errorCode)
      case _ => Seq.empty
    }
  }

  describe("do-notation over a raw nullable inside a record's own method") {
    it("reports E0070 instead of silently dropping the initializer (miscompilation)") {
      val codes = errorCodes(
        """
          |record R(v: String?) {
          |  public:
          |    def f(): String {
          |      val x: String = do[Option] { s <- v(); ret s } ?: "default"
          |      return "result: #{x}"
          |    }
          |}
          |""".stripMargin
      )
      assert(codes.contains("E0070"), s"expected E0070 in $codes")
    }
  }

  describe("do-notation over Option::some/none inside a record's own method") {
    it("desugars and runs correctly, matching the identical code in a class") {
      val result = shell.run(
        """
          |record R(v: Int) {
          |  public:
          |    def f(): Int {
          |      val r = do[Option] { a <- Option::some(v()); b <- Option::some(2); ret (a as Int) + (b as Int) }
          |      return (r.get() as Int)
          |    }
          |}
          |
          |class Test {
          |public:
          |  static def main(args: String[]): Int {
          |    return new R(40).f()
          |  }
          |}
          |""".stripMargin,
        "DoOptionRecordMethod.on",
        Array()
      )
      assert(Shell.Success(42) == result)
    }
  }
}
