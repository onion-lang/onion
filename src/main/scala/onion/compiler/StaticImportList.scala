/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.ArrayList
import java.util.List
import collection.mutable.ArrayBuffer

/**
 * A type safe import list.
 * @author Kota Mizushima
 *
 */
class StaticImportList {
  private val items = new ArrayBuffer[StaticImportItem]

  def add(item: StaticImportItem): Unit =  items += item

  def get(index: Int): StaticImportItem = items(index)

  def getItems: Array[StaticImportItem] = items.toArray

  def size: Int = items.size
}