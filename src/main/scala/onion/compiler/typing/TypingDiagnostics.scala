package onion.compiler.typing

import onion.compiler.*

final class TypingDiagnostics(private val typing: Typing, config: CompilerConfig) {
  import typing.*

  private[compiler] val reporter: SemanticErrorReporter =
    new SemanticErrorReporter(config.maxErrorReports)
  private[compiler] val warningReporter: WarningReporter =
    new WarningReporter(config.warningLevel, config.suppressedWarnings)

  def finishOrThrow(): Unit = {
    val problems = reporter.getProblems
    if (problems.nonEmpty) throw new onion.compiler.exceptions.CompilationException(problems.toSeq)

    warningReporter.printWarnings()

    if (warningReporter.treatAsErrors && warningReporter.hasWarnings) {
      throw new onion.compiler.exceptions.CompilationException(
        Seq(CompileError("", null, s"${warningReporter.warningCount} warning(s) treated as errors"))
      )
    }
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
}
