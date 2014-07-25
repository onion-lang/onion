/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.tools.option

import scala.collection.mutable

/**
 * @author Kota Mizushima
 *
 */
class CommandLineParser(val configs: OptionConfig*) {

  def parse(cmdline: Array[String]): ParseResult = {
    val noArgOpts = mutable.Map[String, AnyRef]()
    val opts = mutable.Map[String, String]()
    val args = mutable.Buffer[String]()
    val lackedOptNames = mutable.Buffer[String]()
    val invalidOptNames = mutable.Buffer[String]()
    var i = 0

    while (i < cmdline.length) {
      if (cmdline(i).startsWith("-")) {
        val param = cmdline(i)
        val config: Option[OptionConfig] = configs.find(_.optionName == param)
        config match {
          case None =>
            invalidOptNames += param
            i += 1
          case Some(config) =>
            if (config.hasArgument) {
              if (i + 1 >= cmdline.length) {
                lackedOptNames += param
              } else {
                opts(param) = cmdline(i + 1)
              }
              i += 2
            } else {
              noArgOpts(param) = new AnyRef
              i += 1
            }
        }
      } else {
        args += cmdline(i)
        i += 1
      }
    }

    if (lackedOptNames.size == 0 && invalidOptNames.size == 0)
      new ParseSuccess(noArgOpts, opts, args.toArray)
    else
      new ParseFailure(lackedOptNames.toArray, invalidOptNames.toArray)
  }
}