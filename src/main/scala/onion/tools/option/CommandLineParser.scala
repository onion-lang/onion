/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.option

import java.util.ArrayList
import java.util.HashMap
import java.util.List
import java.util.Map

/**
 * @author Kota Mizushima
 *         Date: 2005/04/08
 */
class CommandLineParser(confs: Array[OptionConfig]) {

  def parse(cmdline: Array[String]): ParseResult = {
    val noArgOpts: Map[String, AnyRef] = new HashMap[String, AnyRef]
    val opts: Map[String, String] = new HashMap[String, String]
    val args: List[String] = new ArrayList[String]
    val lackedOptNames: List[String] = new ArrayList[String]
    val invalidOptNames: List[String] = new ArrayList[String]
    var i: Int = 0
    i = 0
    while (i < cmdline.length) {
      if (cmdline(i).startsWith("-")) {
        val param: String = cmdline(i)
        val conf: OptionConfig = getConf(param)
        if (conf == null) {
          invalidOptNames.add(param)
          i += 1
        } else if (conf.hasArgument) {
          if (i + 1 >= cmdline.length) {
            lackedOptNames.add(param)
          } else {
            opts.put(param, cmdline(i + 1))
          }
          i += 2
        } else {
          noArgOpts.put(param, new AnyRef)
          i += 1
        }
      } else {
        args.add(cmdline(i))
        i += 1
      }
    }

    if (lackedOptNames.size == 0 && invalidOptNames.size == 0)
      new ParseSuccess(noArgOpts, opts, args)
    else
      new ParseFailure(lackedOptNames.toArray(new Array[String](0)), invalidOptNames.toArray(new Array[String](0)))
  }

  private def getConf(optionName: String): OptionConfig = {
    {
      var i: Int = 0
      while (i < confs.length) {
        {
          if (confs(i).getOptionName == optionName) {
            return confs(i)
          }
        }
        ({
          i += 1;
          i
        })
      }
    }
    return null
  }

}