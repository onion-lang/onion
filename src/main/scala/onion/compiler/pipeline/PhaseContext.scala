package onion.compiler.pipeline

import onion.compiler.{CompileError, CompileWarning, CompilerConfig}
import onion.compiler.diagnostics.DiagnosticBag

import scala.collection.mutable.ArrayBuffer

final class PhaseContext(val config: CompilerConfig) {
  private var currentDiagnostics: DiagnosticBag = DiagnosticBag.empty
  private var currentDebugArtifacts: DebugArtifacts = DebugArtifacts()
  val timings: ArrayBuffer[PhaseTiming] = ArrayBuffer.empty

  def diagnostics: DiagnosticBag =
    currentDiagnostics

  def debugArtifacts: DebugArtifacts =
    currentDebugArtifacts

  def addErrors(errors: Seq[CompileError]): Unit =
    currentDiagnostics = currentDiagnostics.addErrors(errors)

  def addWarnings(warnings: Seq[CompileWarning]): Unit =
    currentDiagnostics = currentDiagnostics.addWarnings(warnings)

  def addInternals(errors: Seq[CompileError]): Unit =
    currentDiagnostics = currentDiagnostics.addInternals(errors)

  def setParsedUnits(units: Seq[onion.compiler.AST.CompilationUnit]): Unit =
    currentDebugArtifacts = currentDebugArtifacts.copy(parsedUnits = Some(units))

  def setRewrittenUnits(units: Seq[onion.compiler.AST.CompilationUnit]): Unit =
    currentDebugArtifacts = currentDebugArtifacts.copy(rewrittenUnits = Some(units))

  def setTypedClasses(classes: Seq[onion.compiler.TypedAST.ClassDefinition]): Unit =
    currentDebugArtifacts = currentDebugArtifacts.copy(typedClasses = Some(classes))

  def setTypedBindings(bindings: Map[onion.compiler.AST.Node, onion.compiler.TypedAST.Node]): Unit =
    currentDebugArtifacts = currentDebugArtifacts.copy(typedBindings = Some(bindings))
}
