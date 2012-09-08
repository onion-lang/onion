/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.util.ArrayList
import java.util.Iterator
import java.lang.Iterable

/**
 * A type safe import list.
 * @author Kota Mizushima
 *
 */
class ImportList extends Iterable[ImportItem] {
  private var items = new ArrayList[ImportItem]

  def add(item: ImportItem): Unit =  items.add(item)
  def get(index: Int): ImportItem = items.get(index)
  def getItems: Array[ImportItem] = items.toArray(new Array[ImportItem](0)).asInstanceOf[Array[ImportItem]]
  def size: Int =  items.size
  def iterator: Iterator[ImportItem] = items.iterator
}