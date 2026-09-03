/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;



import onion.compiler.toolbox.Systems
import scala.collection.mutable
;

/**
 * a Local variable table.
 * @author Kota Mizushima
 */
class LocalScope(val parent: LocalScope) {
  // Allocated on the first `put`: most scopes (loop bodies, branches, lambda bodies) declare
  // nothing, and a scope is opened for every block.
  private var bindings0: mutable.HashMap[String, LocalBinding] = null
  private def bindings: mutable.HashMap[String, LocalBinding] = {
    if (bindings0 == null) bindings0 = mutable.HashMap[String, LocalBinding]()
    bindings0
  }
  private def isEmpty: Boolean = bindings0 == null || bindings0.isEmpty


  /**
   * Gets registered binding objects.
   * @return Set object which element is LocalBinding object
   */
  def entries: mutable.Set[LocalBinding] = {
    if (isEmpty) mutable.HashSet[LocalBinding]() else mutable.HashSet[LocalBinding]() ++ bindings0.values
  }

  /**
   * Tests if this scope contains entry for the given name.
   * @param name
   * @return true if this scope has entry, false otherwise
   */
  def contains(name: String): Boolean = bindings0 != null && bindings0.contains(name)

  /**
   * Gets all variable names in this scope.
   * @return Set of variable names
   */
  def names: Set[String] = if (isEmpty) Set.empty else bindings0.keySet.toSet

  /** The bindings of this scope only, without copying into a Set. */
  def bindingEntries: Iterator[(String, LocalBinding)] = if (isEmpty) Iterator.empty else bindings0.iterator

  /**
   * Gets all variable names in this scope and its ancestors.
   * @return Set of variable names
   */
  def allNames: Set[String] = {
    def collect(scope: LocalScope, acc: Set[String]): Set[String] = {
      if (scope == null) acc
      else collect(scope.parent, acc ++ scope.names)
    }
    collect(this, Set.empty)
  }

  /**
   * Registers binding object to this scope for the given name.
   * @param name
   * @param binding
   * @return true if already putted for given name, false otherwise
   */
  def put(name: String, binding: LocalBinding): Boolean = {
    if(bindings.contains(name)){
      true
    } else {
      bindings.put(name, binding)
      false
    }
  }

  /**
   * Gets the registered binding object from this scope for given name.
   * @param name
   * @return the LocalBinding object if registered, null otherwise
   */
  def get(name: String): Option[LocalBinding] = if (bindings0 == null) None else bindings0.get(name)

  /** As `get`, without the Option. */
  private def getOrNull(name: String): LocalBinding =
    if (bindings0 == null) null else bindings0.getOrElse(name, null)

  /**
   * Finds the registered binding object from this scope and its ancestors
   * for given name.
   * @param name
   * @return the LocalBinding object if found, null otherwise
   */
  def lookup(name: String): LocalBinding = {
    var table = this
    while (table != null) {
      val found = table.getOrNull(name)
      if (found != null) return found
      table = table.parent
    }
    null
  }

  override def toString(): String = {
    val separator = Systems.lineSeparator
    val lines = for(name <- bindings.keySet) yield s"  ${name}:${bindings(name).tp}"
    lines.mkString(s"[${separator}", separator, "]")
  }
}
