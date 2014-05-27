package onion.compiler

import scala.collection.mutable

/**
 * @author Kota Mizushima
 */
class UnOrderedTable[E <: Named] extends AbstractTable[E](new mutable.HashMap[String, E])
