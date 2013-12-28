package onion.compiler

import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.Map
import java.lang.Iterable

class AbstractTable[E <: Named](protected val mapping: Map[String, E]) extends Iterable[E] {
  def add(entry: E): Unit =  mapping.put(entry.name, entry)
  def get(key: String): E =  mapping.get(key)
  def values: List[E] =  new ArrayList[E](mapping.values)
  def iterator: Iterator[E] =  mapping.values.iterator
}