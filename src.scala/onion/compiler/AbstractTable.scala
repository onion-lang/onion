package onion.compiler

import java.util.ArrayList
import java.util.Iterator
import java.util.List
import java.util.Map
import java.lang.Iterable

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/07
 * Time: 2:43:30
 * To change this template use File | Settings | File Templates.
 */
class AbstractTable[E <: Named](protected val mapping: Map[String, E]) extends Iterable[E] {
  def add(entry: E): Unit =  mapping.put(entry.name, entry)
  def get(key: String): E =  mapping.get(key)
  def values: List[E] =  new ArrayList[E](mapping.values)
  def iterator: Iterator[E] =  mapping.values.iterator
}