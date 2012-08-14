/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

/**
 * @author Kota Mizushima
 * Location in a Scala code.
 * @param line line number in the source.  Note that it is 1 origin.
 * @param column column number in the source.  It is 1 origin, too.
 */
final case class Location(line: Int, column: Int)
