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
 *
 */
class CommandLineParser(val configs: OptionConfig*) {

  def parse(cmdline: Array[String]): ParseResult = {
    val noArgOpts = new HashMap[String, AnyRef]
    val opts = new HashMap[String, String]
    val args = new ArrayList[String]
    val lackedOptNames = new ArrayList[String]
    val invalidOptNames = new ArrayList[String]
    var i = 0

    while (i < cmdline.length) {
      if (cmdline(i).startsWith("-")) {
        val param = cmdline(i)
        val config: Option[OptionConfig] = (configs:Seq[OptionConfig]).find(_.optionName == param)
        config match {
          case None =>
            invalidOptNames.add(param)
            i += 1
          case Some(config) =>
            if (config.hasArgument) {
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
}