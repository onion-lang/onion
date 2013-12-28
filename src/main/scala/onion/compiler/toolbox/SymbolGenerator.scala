/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

/**
 * @author Kota Mizushima
 *
 */
class SymbolGenerator(val prefix: String) {
  private var count_ : Int = 0

  def count: Int = count_

  def generate: String = {
    val newSymbol: String = prefix + count_
    count_ += 1
    newSymbol
  }
}