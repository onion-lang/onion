package onion.compiler.typing

import onion.compiler.*
import onion.compiler.typing.session.TypingSession

final class TypingDiagnostics(private val typing: Typing, private val session: TypingSession) {
  private[compiler] val reporter: SemanticErrorReporter = session.global.diagnostics
  private[compiler] val warningReporter: WarningReporter = session.global.warnings

  def finishOrThrow(): Unit = {
    val problems = reporter.getProblems
    if (problems.nonEmpty) throw new onion.compiler.exceptions.CompilationException(problems.toSeq)
  }

  def report(error: SemanticError, location: Location, items: Seq[AnyRef]): Unit = {
    if (!typing.reportingEnabled) return
    reporter.setSourceFile(typing.unit_.sourceFile)
    reporter.report(error, location, items.toArray)
  }

  def reportUnusedVariables(context: LocalContext): Unit = {
    if (!typing.reportingEnabled) return
    warningReporter.setSourceFile(typing.unit_.sourceFile)
    context.unusedLocalVariables.foreach { variable =>
      warningReporter.unusedVariable(variable.location, variable.name)
    }
    context.unusedParameters.foreach { parameter =>
      warningReporter.unusedParameter(parameter.location, parameter.name)
    }
  }

  def checkAndReportShadowing(name: String, location: Location, context: LocalContext): Unit = {
    if (!typing.reportingEnabled) return
    if (name.startsWith("symbol#") || name.startsWith("$")) return

    warningReporter.setSourceFile(typing.unit_.sourceFile)
    context.checkShadowing(name).foreach { originalLocation =>
      warningReporter.shadowedVariable(location, name, originalLocation)
    }
  }

  def problems: Array[CompileError] = reporter.getProblems

  def warnings: Seq[CompileWarning] = warningReporter.getWarnings
}
