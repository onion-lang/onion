package onion.compiler

import scala.collection.Iterable
import scala.collection.Iterator
import scala.collection.mutable

class MultiTable[E <: Named] extends Iterable[E] {
  private[this] final val mapping = new mutable.HashMap[String, mutable.Buffer[E]]

  def add(entry: E): Boolean = {
    mapping.get(entry.name) match {
      case Some(v) =>
        v += entry
        true
      case None =>
        val v = mutable.Buffer[E]()
        v += entry
        mapping(entry.name) = v
        false
    }
  }

  def get(key: String): Seq[E] = {
    (mapping.get(key) match {
      case None =>
        val v = mutable.Buffer[E]()
        mapping(key) = v
        v
      case Some(v) =>
        v
    }).toList
  }

  def values: Seq[E] =  mapping.values.toList.flatten

  def iterator: Iterator[E] = values.iterator
}