package onion.compiler.backend.asm

import org.objectweb.asm.Label

import scala.collection.mutable

/**
  * Tracks loop start/end labels (optionally named, for labeled
  * break/continue) for break/continue handling.
  */
final class LoopContext {
  private case class Entry(name: String, start: Label, end: Label)
  private val entries = mutable.Stack[Entry]()

  def currentStart: Option[Label] = entries.headOption.map(_.start)
  def currentEnd: Option[Label] = entries.headOption.map(_.end)

  def startOf(name: String): Option[Label] = entries.find(_.name == name).map(_.start)
  def endOf(name: String): Option[Label] = entries.find(_.name == name).map(_.end)

  def push(start: Label, end: Label): Unit = push(null, start, end)

  def push(name: String, start: Label, end: Label): Unit = {
    entries.push(Entry(name, start, end))
  }

  def pop(): Unit = {
    entries.pop()
  }
}
