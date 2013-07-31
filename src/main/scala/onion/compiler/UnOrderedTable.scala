package onion.compiler

import java.util.HashMap

/**
 * @author Kota Mizushima
 */
class UnorderedTable[E <: Named] extends AbstractTable[E](new HashMap[String, E])
