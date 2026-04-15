package onion.compiler.diagnostics

import onion.compiler.{CompileError, CompileWarning}

final case class DiagnosticBag(
  errors: Vector[CompileError] = Vector.empty,
  warnings: Vector[CompileWarning] = Vector.empty,
  internals: Vector[CompileError] = Vector.empty
) {
  def hasErrors: Boolean =
    errors.nonEmpty || internals.nonEmpty

  def allErrors: Vector[CompileError] =
    errors ++ internals

  def addErrors(next: Seq[CompileError]): DiagnosticBag =
    copy(errors = errors ++ next)

  def addWarnings(next: Seq[CompileWarning]): DiagnosticBag =
    copy(warnings = warnings ++ next)

  def addInternals(next: Seq[CompileError]): DiagnosticBag =
    copy(internals = internals ++ next)
}

object DiagnosticBag {
  val empty: DiagnosticBag = DiagnosticBag()
}
