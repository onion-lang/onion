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
public class ParseFailure implements ParseResult{  
  private final String[] lackedOptions;
  private final String[] invalidOptions;

  public ParseFailure(String[] lackedOptions, String[] invalidOptions){
    this.lackedOptions = lackedOptions;
    this.invalidOptions = invalidOptions;
  }
  
  public int getStatus() {
    return FAILURE;
  }
  
  public String[] getLackedOptions(){
    return lackedOptions;
  }
  
  public String[] getInvalidOptions(){
    return invalidOptions;
  }
  
}
