/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.compiler;

import java.io.IOException;
import java.io.Reader;

public interface InputSource {
  Reader openReader() throws IOException;
  String getName();
}
