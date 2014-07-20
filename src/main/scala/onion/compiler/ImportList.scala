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
  private[this] val buffer = mutable.Buffer[ImportItem]()

  def add(item: ImportItem): Unit = buffer += item
  def get(index: Int): ImportItem = buffer(index)
  def items: Array[ImportItem] = buffer.toArray
  override def size: Int = buffer.size
  def iterator: Iterator[ImportItem] = buffer.iterator
}