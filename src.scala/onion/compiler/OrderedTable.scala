package onion.compiler

import java.util.LinkedHashMap

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/06
 * Time: 23:09:43
 * To change this template use File | Settings | File Templates.
 */
class OrderedTable[E <: Named] extends AbstractTable[E](new LinkedHashMap[String, E])
