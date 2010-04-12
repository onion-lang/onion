/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package org.onion_lang.onion.tools.option;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Kota Mizushima
 * Date: 2005/04/08
 */
public class CommandLineParser {
  private final OptionConf[] confs;
  
  public CommandLineParser(OptionConf[] confs) {
    this.confs = confs;
  }
  
  public ParseResult parse(String[] cmdline){
    Map noArgOpts = new HashMap();
    Map opts = new HashMap();
    List args = new ArrayList();
    List lackedOptNames = new ArrayList();
    List invalidOptNames = new ArrayList();
    {
      int i;
      for(i = 0; i < cmdline.length && cmdline[i].startsWith("-");) {
        String param = cmdline[i];
        OptionConf conf = getConf(param);
        if(conf == null){
          invalidOptNames.add(param);
          i++;
        }else if(conf.hasArgument()){
          if(i + 1 >= cmdline.length){
            lackedOptNames.add(param);
          }else{
            opts.put(param, cmdline[i + 1]);
          }
          i+=2;
        }else{
          noArgOpts.put(param, new Object());
          i++;
        }
      }
      for(; i < cmdline.length; i++) {
        args.add(cmdline[i]);
      }
    }
    if(lackedOptNames.size() == 0 && invalidOptNames.size() == 0){
      return new ParseSuccess(noArgOpts, opts, args);
    }else{
      return new ParseFailure(
        (String[])lackedOptNames.toArray(new String[0]), 
        (String[])invalidOptNames.toArray(new String[0]));
    }
  }
  
  private OptionConf getConf(String optionName){
    for(int i = 0; i < confs.length; i++){
      if(confs[i].getOptionName().equals(optionName)){
        return confs[i];
      }
    }
    return null;
  }
}