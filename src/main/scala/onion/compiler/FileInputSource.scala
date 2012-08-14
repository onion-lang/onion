/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import java.io.IOException
import java.io.Reader
import onion.compiler.toolbox.Inputs

class FileInputSource(val file: String) extends InputSource {
  def openReader: Reader = {
    if (reader == null) reader = Inputs.newReader(file)
    return reader
  }

  def getName: String = {
    return file
  }

  private var reader: Reader = null
}