/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.optimization

import scala.collection.mutable

/**
 * Strongly Connected Components detection using Tarjan's algorithm.
 *
 * This is a generic implementation that can be used for any node type.
 * Used by MutualRecursionOptimization to detect mutual recursion groups.
 */
private[optimization] object StronglyConnectedComponents {

  /**
   * Find all strongly connected components in a directed graph.
   *
   * @param nodes All nodes in the graph
   * @param successors Function that returns successor nodes for a given node
   * @return Sequence of SCCs, each containing 2+ nodes (single-node SCCs are filtered out)
   */
  def findSCCs[T](nodes: Seq[T], successors: T => Set[T]): Seq[Seq[T]] = {
    val index = mutable.Map[T, Int]()
    val lowLink = mutable.Map[T, Int]()
    val onStack = mutable.Set[T]()
    val stack = mutable.Stack[T]()
    val sccs = mutable.ArrayBuffer[Seq[T]]()
    var currentIndex = 0

    def strongConnect(node: T): Unit = {
      index(node) = currentIndex
      lowLink(node) = currentIndex
      currentIndex += 1
      stack.push(node)
      onStack += node

      // Visit successors
      successors(node).foreach { successor =>
        if (!index.contains(successor)) {
          strongConnect(successor)
          lowLink(node) = math.min(lowLink(node), lowLink(successor))
        } else if (onStack.contains(successor)) {
          lowLink(node) = math.min(lowLink(node), index(successor))
        }
      }

      // If node is root of SCC, pop the stack
      if (lowLink(node) == index(node)) {
        val scc = mutable.ArrayBuffer[T]()
        var w: T = null.asInstanceOf[T]
        var continue = true
        while (continue) {
          w = stack.pop()
          onStack -= w
          scc += w
          if (w == node) continue = false
        }

        // Only include SCCs with 2+ nodes (mutual recursion requires at least 2 methods)
        if (scc.size > 1) {
          sccs += scc.toSeq
        }
      }
    }

    nodes.foreach { node =>
      if (!index.contains(node)) {
        strongConnect(node)
      }
    }

    sccs.toSeq
  }
}
