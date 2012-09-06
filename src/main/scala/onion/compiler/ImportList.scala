/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.       *
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
  private val items_ = new ArrayList[ImportItem]

  def add(item: ImportItem): Unit =  items_.add(item)
  def apply(index: Int): ImportItem = items_.get(index)
  def items: Array[ImportItem] = items_.toArray(new Array[ImportItem](0)).asInstanceOf[Array[ImportItem]]
  def size: Int =  items_.size
  def iterator: Iterator[ImportItem] = items_.iterator
}