/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler.toolbox

/**
 * @author Kota Mizushima
 *         Date: 2005/07/07
 */
class SymbolGenerator(val prefix: String) {
  private var count: Int = 0

  def getPrefix: String = prefix

  def getCount: Int = count

  def generate: String = {
    val newSymbol: String = prefix + count
    count += 1
    newSymbol
  }
}