package onion.compiler.diagnostics

import onion.compiler.CompileError

import scala.collection.mutable.ArrayBuffer

final class ErrorCollector {
  private val buffer = ArrayBuffer.empty[CompileError]

  def add(error: CompileError): Unit =
    buffer += error

  def addAll(errors: IterableOnce[CompileError]): Unit =
    buffer ++= errors

  def result(): Vector[CompileError] =
    buffer.toVector
}
