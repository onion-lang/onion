/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import scala.collection.mutable

/**
 * A type safe import list.
 * @author Kota Mizushima
 *
 */
class ImportList extends Iterable[ImportItem] {
  private var items = mutable.Buffer[ImportItem]()

  def add(item: ImportItem): Unit =  items += item
  def get(index: Int): ImportItem = items(index)
  def getItems: Array[ImportItem] = items.toArray
  override def size: Int = items.size
  def iterator: Iterator[ImportItem] = items.iterator
}