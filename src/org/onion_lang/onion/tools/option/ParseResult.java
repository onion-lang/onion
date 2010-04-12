/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.tools.option;


/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public interface ParseResult {
  int SUCCEED = 0;
  int FAILURE = 1;
  int getStatus();
}
