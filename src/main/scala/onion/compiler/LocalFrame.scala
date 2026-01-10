/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.{Arrays => JArrays}
import scala.collection.mutable

/**
 * @author Kota Mizushima
 *
 */
class LocalFrame(val parent: LocalFrame) {
  var scope = new LocalScope(null)
  var closed: Boolean = false

  private val allScopes: mutable.Buffer[LocalScope] = mutable.Buffer[LocalScope]()
  private var maxIndex: Int = 0

  allScopes += scope

  def open[A](block: => A): A = {
    try {
      scope = new LocalScope(scope)
      allScopes += scope
      block
    } finally {
      scope = scope.parent
    }
  }

  def entries: Seq[LocalBinding] = {
    val binds: Array[LocalBinding] = entrySet.toArray
    JArrays.sort(binds, (b1: LocalBinding, b2: LocalBinding) => {
      val i1 = b1.index
      val i2 = b2.index
      if (i1 < i2) -1 else if (i1 > i2) 1 else 0
    })
    binds.toSeq
  }

  def add(name: String, `type` : TypedAST.Type): Int = {
    add(name, `type`, isMutable = true, isBoxed = false)
  }

  def add(name: String, `type`: TypedAST.Type, isMutable: Boolean): Int = {
    add(name, `type`, isMutable, isBoxed = false)
  }

  def add(name: String, `type`: TypedAST.Type, isMutable: Boolean, isBoxed: Boolean): Int = {
    val bind = scope.get(name)
    bind match {
      case Some(_) => -1
      case None =>
        val index = maxIndex
        maxIndex += 1
        scope.put(name, LocalBinding(index, `type`, isMutable, isBoxed))
        index
    }
  }

  /** Iterator over frame hierarchy starting from this frame */
  private def frames: Iterator[LocalFrame] =
    Iterator.iterate(this)(_.parent).takeWhile(_ != null)

  /** Tail-recursive lookup through frame hierarchy */
  @scala.annotation.tailrec
  private def lookupInFrames(frame: LocalFrame, frameIndex: Int, name: String): ClosureLocalBinding =
    if (frame == null) null
    else frame.scope.lookup(name) match {
      case null => lookupInFrames(frame.parent, frameIndex + 1, name)
      case binding => new ClosureLocalBinding(frameIndex, binding.index, binding.tp, binding.isMutable, binding.isBoxed)
    }

  def lookup(name: String): ClosureLocalBinding = lookupInFrames(this, 0, name)

  def lookupOnlyCurrentScope(name: String): ClosureLocalBinding = {
    scope.get(name).map { binding =>
      new ClosureLocalBinding(0, binding.index, binding.tp, binding.isMutable, binding.isBoxed)
    }.orNull
  }

  /** Gets all variable names visible from the current scope (including parent frames) */
  def allNames: Set[String] = frames.flatMap(_.scope.allNames).toSet

  def setAllClosed(closed: Boolean): Unit = frames.foreach(_.closed = closed)

  def depth: Int = frames.length - 1

  private def entrySet: mutable.Set[LocalBinding] = {
    val entries = new mutable.HashSet[LocalBinding]()
    mutable.Set[LocalBinding]() ++ (for (s1 <- allScopes; s2 <- s1.entries) yield s2)
  }

}
