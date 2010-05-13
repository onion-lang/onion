/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.option;

/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public class OptionConf {
  private final String optionName;
  private final boolean hasArgument;

  public OptionConf(String optionName, boolean hasArgument) {
    this.optionName = optionName;
    this.hasArgument = hasArgument;
  }
  
  public String getOptionName(){
    return optionName;
  }
  
  public boolean hasArgument(){
    return hasArgument;
  }
}
