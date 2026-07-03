package onion.compiler.backend.asm

import org.objectweb.asm.Label

import scala.collection.mutable

/**
  * Tracks loop start/end labels (optionally named, for labeled
  * break/continue) for break/continue handling.
  */
final class LoopContext {
  // finallyDepth: the enclosing finally-stack depth when the loop was entered, so
  // break/continue can run the finally blocks entered inside the loop before jumping.
  private case class Entry(name: String, start: Label, end: Label, finallyDepth: Int)
  private val entries = mutable.Stack[Entry]()

  def currentStart: Option[Label] = entries.headOption.map(_.start)
  def currentEnd: Option[Label] = entries.headOption.map(_.end)

  def startOf(name: String): Option[Label] = entries.find(_.name == name).map(_.start)
  def endOf(name: String): Option[Label] = entries.find(_.name == name).map(_.end)

  def currentFinallyDepth: Option[Int] = entries.headOption.map(_.finallyDepth)
  def finallyDepthOf(name: String): Option[Int] = entries.find(_.name == name).map(_.finallyDepth)

  def push(start: Label, end: Label, finallyDepth: Int): Unit = push(null, start, end, finallyDepth)

  def push(name: String, start: Label, end: Label, finallyDepth: Int): Unit = {
    entries.push(Entry(name, start, end, finallyDepth))
  }

  def pop(): Unit = {
    entries.pop()
  }
}
