/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.option

/**
 * @author Kota Mizushima
 *         Date: 2005/04/08
 */
class ParseFailure(lackedOptions: Array[String], invalidOptions: Array[String]) extends ParseResult {
  def getStatus: Int = ParseResult.FAILURE

  def getLackedOptions: Array[String] = lackedOptions

  def getInvalidOptions: Array[String] = invalidOptions
}