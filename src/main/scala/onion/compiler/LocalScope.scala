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
  private val bindings = mutable.HashMap[String, LocalBinding]()


  /**
   * Gets registered binding objects.
   * @return Set object which element is LocalBinding object
   */
  def entries: mutable.Set[LocalBinding] = {
    mutable.HashSet[LocalBinding]() ++ bindings.values
  }

  /**
   * Tests if this scope contains entry for the given name.
   * @param name
   * @return true if this scope has entry, false otherwise
   */
  def contains(name: String): Boolean = bindings.contains(name)

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
  def get(name: String): Option[LocalBinding] = bindings.get(name)

  /**
   * Finds the registered binding object from this scope and its ancestors
   * for given name.
   * @param name
   * @return the LocalBinding object if found, null otherwise
   */
  def lookup(name: String): LocalBinding = {
    def find(table: LocalScope): LocalBinding = {
      if (table == null) {
        null
      } else if(table.contains(name)) {
        table.get(name).get
      } else {
        find(table.parent)
      }
    }
    find(this)
  }

  override def toString(): String = {
    val separator = Systems.lineSeparator
    val lines = for(name <- bindings.keySet) yield s"  ${name}:${bindings(name).tp}"
    lines.mkString(s"[${separator}", separator, "]")
  }
}
