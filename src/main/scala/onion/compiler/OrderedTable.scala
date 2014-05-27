package onion.compiler

import scala.collection.mutable

class OrderedTable[E <: Named] extends AbstractTable[E](new mutable.LinkedHashMap[String, E])
