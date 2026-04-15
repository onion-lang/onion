/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.source.ReaderSource

import java.io.Reader

class StreamInputSource(readerFactory: () => Reader, name: String)
  extends ReaderSource(readerFactory, name)
  with InputSource
