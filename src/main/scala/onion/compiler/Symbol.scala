/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import collection.mutable.HashMap

/**
 *
 * @author Kota Mizushima
 *
 */
object Symbol {
  private val symbols = new HashMap[String, Symbol]
  def apply(name: String): Symbol = {
    symbols.get(name) match {
      case Some(symbol) => symbol
      case None =>
        val symbol = new Symbol(name)
        symbols(name) = symbol
        symbol
    }
  }

}

/**
 * Users cannot create Symbol object directly.
 * @param name
 */
final class Symbol private (val name: String) extends AnyRef {
  override def equals(obj: Any): Boolean = this eq obj.asInstanceOf[AnyRef]
  override def hashCode(): Int = super.hashCode()
}