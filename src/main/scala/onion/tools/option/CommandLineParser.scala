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
    val opts = mutable.Map[String, CommandLineParam]()
    val args = mutable.Buffer[String]()
    val lackedOptNames = mutable.Buffer[ValuedParam]()
    val invalidOptNames = mutable.Buffer[ValuedParam]()
    var i = 0

    while (i < cmdline.length) {
      if (cmdline(i).startsWith("-")) {
        val param = cmdline(i)
        val config: Option[OptionConfig] = configs.find(_.optionName == param)
        config match {
          case None =>
            invalidOptNames += ValuedParam(param)
            i += 1
          case Some(config) =>
            if (config.hasArgument) {
              if (i + 1 >= cmdline.length) {
                lackedOptNames += ValuedParam(param)
              } else {
                opts(param) = ValuedParam(cmdline(i + 1))
              }
              i += 2
            } else {
              opts(param) = NoValuedParam
              i += 1
            }
        }
      } else {
        args += cmdline(i)
        i += 1
      }
    }

    if (lackedOptNames.size == 0 && invalidOptNames.size == 0)
      new ParseSuccess(opts, args.toArray)
    else
      new ParseFailure(lackedOptNames.toArray, invalidOptNames.toArray)
  }
}