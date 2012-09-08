/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.option

import java.util.List
import java.util.Map

/**
 * @author Kota Mizushima
 *         Date: 2005/04/08
 */
class ParseSuccess(noArgumentOptions: Map[_, _], options: Map[_, _], arguments: List[_]) extends ParseResult {
  def getStatus: Int = ParseResult.SUCCEED

  def getNoArgumentOptions: Map[_, _] = noArgumentOptions

  def getOptions: Map[_, _] = options

  def getArguments: List[_] = arguments
}