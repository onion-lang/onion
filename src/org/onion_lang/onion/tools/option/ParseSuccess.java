/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.tools.option;

import java.util.List;
import java.util.Map;

/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public class ParseSuccess implements ParseResult {
  private Map options;
  private Map noArgumentOptions;
  private List arguments;

  public ParseSuccess(Map noArgumentOptions, Map options, List arguments) {
    this.noArgumentOptions = noArgumentOptions;
    this.options = options;
    this.arguments = arguments;
  }

  public int getStatus() {
    return SUCCEED;
  }
  
  public Map getNoArgumentOptions(){
    return noArgumentOptions;
  }

  public Map getOptions(){
    return options;
  }
  
  public List getArguments(){
    return arguments;
  }
  
}
