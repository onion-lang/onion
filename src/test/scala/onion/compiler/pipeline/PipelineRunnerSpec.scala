package onion.compiler.pipeline

import onion.compiler.*
import onion.compiler.source.SourceHandle
import org.scalatest.funspec.AnyFunSpec
import org.scalatest.matchers.should.Matchers

import java.io.StringReader

class PipelineRunnerSpec extends AnyFunSpec with Matchers {
  describe("PipelineRunner") {
    it("runs the default phases in the fixed order") {
      val config = CompilerConfig(Seq("."), "", "UTF-8", "", 10)
      val request = CompilationRequest(
        Seq(new StreamInputSource(() => new StringReader("""IO::println("ok")"""), "Order.on")),
        config
      )

      val result = new PipelineRunner(PipelineRunner.defaultPhases(config)).run(request)

      result.hasErrors shouldBe false
      result.timings.map(_.name) shouldBe Seq(
        "Parsing",
        "Rewriting",
        "Typing",
        "TailCallOptimization",
        "MutualRecursionOptimization",
        "BytecodeGeneration"
      )
    }

    it("stops after a failing phase and records an internal diagnostic") {
      val phases = CompilationPhases(
        parsing = new CompilerPhase[Seq[SourceHandle], Seq[AST.CompilationUnit]] {
          override val name: String = "Parsing"
          override def run(input: Seq[SourceHandle], ctx: PhaseContext): Seq[AST.CompilationUnit] = Seq.empty
        },
        rewriting = new CompilerPhase[Seq[AST.CompilationUnit], Seq[AST.CompilationUnit]] {
          override val name: String = "Rewriting"
          override def run(input: Seq[AST.CompilationUnit], ctx: PhaseContext): Seq[AST.CompilationUnit] =
            throw new IllegalStateException("boom")
        },
        typing = new CompilerPhase[Seq[AST.CompilationUnit], onion.compiler.typing.TypingPhaseResult] {
          override val name: String = "Typing"
          override def run(input: Seq[AST.CompilationUnit], ctx: PhaseContext): onion.compiler.typing.TypingPhaseResult =
            fail("typing phase should not run after a rewriting failure")
        },
        tailCallOptimization = new CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[TypedAST.ClassDefinition]] {
          override val name: String = "TailCallOptimization"
          override def run(input: Seq[TypedAST.ClassDefinition], ctx: PhaseContext): Seq[TypedAST.ClassDefinition] =
            fail("tail-call optimization should not run after a rewriting failure")
        },
        mutualRecursionOptimization = new CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[TypedAST.ClassDefinition]] {
          override val name: String = "MutualRecursionOptimization"
          override def run(input: Seq[TypedAST.ClassDefinition], ctx: PhaseContext): Seq[TypedAST.ClassDefinition] =
            fail("mutual recursion optimization should not run after a rewriting failure")
        },
        bytecodeGeneration = new CompilerPhase[Seq[TypedAST.ClassDefinition], Seq[CompiledClass]] {
          override val name: String = "BytecodeGeneration"
          override def run(input: Seq[TypedAST.ClassDefinition], ctx: PhaseContext): Seq[CompiledClass] =
            fail("bytecode generation should not run after a rewriting failure")
        }
      )

      val result = new PipelineRunner(phases).run(
        CompilationRequest(
          Seq(new StreamInputSource(() => new StringReader(""), "Broken.on")),
          CompilerConfig(Seq("."), "", "UTF-8", "", 10)
        )
      )

      result.hasErrors shouldBe true
      result.timings.map(_.name) shouldBe Seq("Parsing", "Rewriting")
      result.diagnostics.internals should have size 1
      result.diagnostics.internals.head.message should include ("Internal compiler error in Rewriting")
    }

    it("promotes warnings to errors when warning level is error") {
      val config = CompilerConfig(Seq("."), "", "UTF-8", "", 10, warningLevel = WarningLevel.Error)
      val request = CompilationRequest(
        Seq(
          new StreamInputSource(
            () =>
              new StringReader(
                """
                  |class Main {
                  |public:
                  |  static def main(args: String[]): String {
                  |    return "ok"
                  |  }
                  |}
                  |""".stripMargin
              ),
            "WarnAsError.on"
          )
        ),
        config
      )

      val result = new PipelineRunner(PipelineRunner.defaultPhases(config)).run(request)

      result.diagnostics.warnings should not be empty
      result.hasErrors shouldBe true
      result.classes shouldBe empty
    }
  }
}
