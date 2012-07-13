/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util._
import scala.collection.JavaConverters._

/**
 * @author Kota Mizushima
 * Date: 2005/06/28
 */
class LocalFrame(val parent: LocalFrame) {
  var scope = new LocalScope(null)
  private var allScopes: List[LocalScope] = new ArrayList[LocalScope]
  allScopes.add(scope)
  private var maxIndex: Int = 0
  private var closed: Boolean = false

  /**
   * Opens a new scope.
   */
  def openScope: Unit = {
    scope = new LocalScope(scope)
    allScopes.add(scope)
  }

  def open[A](block: => A): A = {
    try {
      scope = new LocalScope(scope)
      allScopes.add(scope)
    } finally {
      scope = scope.parent
    }
  }

  /**
   * Closes the current scope.
   */
  def closeScope: Unit = {
    scope = scope.parent
  }

  private[compiler] def getScope: LocalScope = {
    return scope
  }

  def entries: Array[LocalBinding] = {
    val entries: Set[LocalBinding] = entrySet
    val binds: Array[LocalBinding] = new Array[LocalBinding](entries.size)
    val iterator: Iterator[LocalBinding] = entries.iterator

    {
      var i: Int = 0
      while (i < binds.length) {
        {
          binds(i) = iterator.next
        }
        ({
          i += 1; i
        })
      }
    }

    Arrays.sort(binds, new Comparator[LocalBinding] {
      def compare(b1: LocalBinding, b2: LocalBinding): Int = {
        val i1 = b1.getIndex
        val i2 = b2.getIndex
        return if (i1 < i2) -1 else if (i1 > i2) 1 else 0
      }
    })
    binds
  }

  def add(name: String, `type` : IRT.TypeRef): Int = {
    val bind = scope.get(name)
    if (bind != null) return -1
    val index = maxIndex
    ({
      maxIndex += 1; maxIndex
    })
    scope.put(name, new LocalBinding(index, `type`))
    index
  }

  def lookup(name: String): ClosureLocalBinding = {
    var frame: LocalFrame = this
    var frameIndex: Int = 0
    while (frame != null) {
      val binding = frame.scope.lookup(name)
      if (binding != null) {
        return new ClosureLocalBinding(frameIndex, binding.getIndex, binding.getType)
      }
      ({
        frameIndex += 1; frameIndex
      })
      frame = frame.parent
    }
    null
  }

  def lookupOnlyCurrentScope(name: String): ClosureLocalBinding = {
    val binding = scope.get(name)
    if (binding != null) {
      return new ClosureLocalBinding(0, binding.getIndex, binding.getType)
    }
    null
  }

  def setAllClosed(closed: Boolean): Unit = {
    var frame: LocalFrame = this
    while (frame != null) {
      frame.setClosed(closed)
      frame = frame.parent
    }
  }

  def setClosed(closed: Boolean): Unit = {
    this.closed = closed
  }

  def isClosed: Boolean = {
    closed
  }

  def depth: Int = {
    var frame: LocalFrame = this
    var depth: Int = -1
    while (frame != null) {
      ({
        depth += 1; depth
      })
      frame = frame.parent
    }
    depth
  }

  private def entrySet: Set[LocalBinding] = {
    val entries = new HashSet[LocalBinding]
    for (s <- allScopes.asScala) {
      entries.addAll(s.entries)
    }
    entries
  }

}