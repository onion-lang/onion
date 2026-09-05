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
  // Most scopes are empty or contain one parameter/local. Keep that binding inline;
  // larger scopes retain the existing hash-based lookup and iteration behavior.
  private var bindings0: mutable.HashMap[String, LocalBinding] = null
  private var hasFirst = false
  private var firstName: String = null
  private var firstBinding: LocalBinding = null


  /**
   * Gets registered binding objects.
   * @return Set object which element is LocalBinding object
   */
  def entries: mutable.Set[LocalBinding] = {
    if (bindings0 != null) mutable.HashSet[LocalBinding]() ++ bindings0.values
    else if (hasFirst) mutable.HashSet(firstBinding)
    else mutable.HashSet[LocalBinding]()
  }

  /**
   * Tests if this scope contains entry for the given name.
   * @param name
   * @return true if this scope has entry, false otherwise
   */
  def contains(name: String): Boolean =
    if (bindings0 != null) bindings0.contains(name)
    else hasFirst && java.util.Objects.equals(firstName, name)

  /**
   * Gets all variable names in this scope.
   * @return Set of variable names
   */
  def names: Set[String] =
    if (bindings0 != null) bindings0.keySet.toSet
    else if (hasFirst) Set(firstName)
    else Set.empty

  /** The bindings of this scope only, without copying into a Set. */
  def bindingEntries: Iterator[(String, LocalBinding)] =
    if (bindings0 != null) bindings0.iterator
    else if (hasFirst) Iterator.single(firstName -> firstBinding)
    else Iterator.empty

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
    if (contains(name)) {
      true
    } else if (bindings0 != null) {
      bindings0.put(name, binding)
      false
    } else if (hasFirst) {
      val grown = mutable.HashMap[String, LocalBinding]()
      grown.put(firstName, firstBinding)
      grown.put(name, binding)
      bindings0 = grown
      firstName = null
      firstBinding = null
      hasFirst = false
      false
    } else {
      firstName = name
      firstBinding = binding
      hasFirst = true
      false
    }
  }

  /**
   * Gets the registered binding object from this scope for given name.
   * @param name
   * @return the LocalBinding object if registered, null otherwise
   */
  def get(name: String): Option[LocalBinding] =
    if (bindings0 != null) bindings0.get(name)
    else if (contains(name)) Some(firstBinding)
    else None

  /** As `get`, without the Option. */
  private def getOrNull(name: String): LocalBinding =
    if (bindings0 != null) bindings0.getOrElse(name, null)
    else if (contains(name)) firstBinding
    else null

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
    val lines =
      if (bindings0 != null) for (name <- bindings0.keySet) yield s"  ${name}:${bindings0(name).tp}"
      else if (hasFirst) Set(s"  ${firstName}:${firstBinding.tp}")
      else Set.empty[String]
    lines.mkString(s"[${separator}", separator, "]")
  }
}
