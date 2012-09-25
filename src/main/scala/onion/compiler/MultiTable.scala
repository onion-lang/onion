package onion.compiler

import java.util._
import java.util.Iterator
import java.lang.Iterable
import scala.collection.JavaConverters._

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/07
 * Time: 2:48:39
 * To change this template use File | Settings | File Templates.
 */
class MultiTable[E <: Named] extends Iterable[E] {
  private final val mapping = new HashMap[String, List[E]]

  def add(entry: E): Boolean = {
    var value: List[E] = mapping.get(entry.name)
    if (value == null) {
      value = new ArrayList[E]
      value.add(entry)
      mapping.put(entry.name, value)
      false
    }
    else {
      value.add(entry)
      true
    }
  }

  def get(key: String): List[E] = {
    var values: List[E] = mapping.get(key)
    if (values == null) {
      values = new ArrayList[E]
      mapping.put(key, values)
    }
    values
  }

  def values: List[E] = {
    val list = new ArrayList[E]
    for (value <- mapping.values.asScala) list.addAll(value)
    list
  }

  def iterator: Iterator[E] = values.iterator
}