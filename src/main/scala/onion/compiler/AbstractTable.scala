package onion.compiler

import scala.collection.mutable

class AbstractTable[E <: Named](protected val mapping: mutable.Map[String, E]) extends Iterable[E] {
  def add(entry: E): Unit =  mapping(entry.name) = entry
  def get(key: String): Option[E] =  mapping.get(key)
  /** As `get`, without the Option: the class table looks names up on every resolution. */
  def getOrNull(key: String): E = mapping.getOrElse(key, null.asInstanceOf[E])
  def values: Seq[E] =  mapping.values.toList
  def iterator: Iterator[E] =  mapping.values.iterator
}