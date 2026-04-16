package onion.compiler.diagnostics

final class DiagnosticException(val diagnostics: DiagnosticBag)
  extends RuntimeException(s"Compilation produced ${diagnostics.allErrors.size} diagnostic error(s)")
