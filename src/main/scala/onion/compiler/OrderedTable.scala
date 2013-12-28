package onion.compiler

import java.util.LinkedHashMap

class OrderedTable[E <: Named] extends AbstractTable[E](new LinkedHashMap[String, E])
