/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import collection.mutable.HashMap


class SymbolTable {
  private val table = new HashMap[Symbol, AnyRef]

  def put(key: Symbol, value: AnyRef): Unit =  table.put(key, value)

  def get(key: Symbol): AnyRef = table.get(key)

  def containsKey(key: Symbol): Boolean = table.contains(key)
}