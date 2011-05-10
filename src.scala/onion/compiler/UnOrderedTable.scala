package onion.compiler

import java.util.HashMap

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/07
 * Time: 2:47:03
 * To change this template use File | Settings | File Templates.
 */
class UnOrderedTable[E <: Named] extends AbstractTable[E](new HashMap[String, E])
