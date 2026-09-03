package onion.compiler

import scala.collection.Iterable
import scala.collection.Iterator
import scala.collection.mutable

class MultiTable[E <: Named] extends Iterable[E] {
  // Entries per name are kept as an immutable List, appended on add (adds are rare and the
  // lists are short -- they hold one name's overloads) so that `get`, which method
  // resolution calls for every class up a receiver's hierarchy on every call, returns the
  // list itself. The previous shape allocated on every lookup: a hit copied the buffer with
  // toList, and a *miss* inserted an empty buffer into the map -- a read that grew the
  // table for every name ever asked of every class, `size` on `Object` included.
  private[this] final val mapping = new mutable.HashMap[String, List[E]]

  // `values` is asked for on every method lookup over a user class; it is rebuilt only
  // after an `add`.
  private[this] var cachedValues: List[E] = null

  def add(entry: E): Boolean = {
    cachedValues = null
    mapping.get(entry.name) match {
      case Some(v) =>
        mapping(entry.name) = v :+ entry
        true
      case None =>
        mapping(entry.name) = entry :: Nil
        false
    }
  }

  def get(key: String): Seq[E] = mapping.getOrElse(key, Nil)

  def values: Seq[E] = {
    if (cachedValues == null) cachedValues = mapping.values.toList.flatten
    cachedValues
  }
  def iterator: Iterator[E] = values.iterator
}
