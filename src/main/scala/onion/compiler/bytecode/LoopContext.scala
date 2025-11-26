package onion.compiler.bytecode

import org.objectweb.asm.Label

import scala.collection.mutable

/**
  * Tracks loop start/end labels for break/continue handling.
  */
final class LoopContext {
  private val starts = mutable.Stack[Label]()
  private val ends = mutable.Stack[Label]()

  def currentStart: Option[Label] = starts.headOption
  def currentEnd: Option[Label] = ends.headOption

  def push(start: Label, end: Label): Unit = {
    starts.push(start)
    ends.push(end)
  }

  def pop(): Unit = {
    starts.pop()
    ends.pop()
  }
}

