package onion.compiler.diagnostics

import onion.compiler.CompileWarning

import scala.collection.mutable.ArrayBuffer

final class WarningCollector {
  private val buffer = ArrayBuffer.empty[CompileWarning]

  def add(warning: CompileWarning): Unit =
    buffer += warning

  def addAll(warnings: IterableOnce[CompileWarning]): Unit =
    buffer ++= warnings

  def result(): Vector[CompileWarning] =
    buffer.toVector
}
