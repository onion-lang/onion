/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.source.FileSource

import java.nio.charset.Charset

class FileInputSource(name: String, charset: Charset) extends FileSource(name, charset) with InputSource {
  def this(name: String) = this(name, Charset.defaultCharset())
}
