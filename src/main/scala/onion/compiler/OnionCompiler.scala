/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import _root_.scala.collection.JavaConverters._
import _root_.scala.collection.Iterator
import java.io.BufferedReader
import java.io.IOException
import java.io.FileNotFoundException
import java.text.MessageFormat
import onion.compiler.toolbox._
import onion.compiler.exceptions.CompilationException

/**
 * @author Kota Mizushima
 *
 */
class OnionCompiler(val config: CompilerConfig) {

  def compile(fileNames: Array[String]): Seq[CompiledClass] = {
    compile(fileNames.map{new FileInputSource(_)}.toSeq)
  }

  def compile(srcs: Seq[InputSource]): Seq[CompiledClass] = {
    try {
      (new Parsing(config) andThen new Rewriting(config) andThen new Typing(config) andThen new Generating(config)).process(srcs)
    } catch {
      case e: CompilationException =>
        for (error <- e.problems) printError(error)
        System.err.println(Message("error.count", e.size))
        null
    }
  }

  private def printError(error: CompileError): Unit = {
    val location = error.location
    val sourceFile = error.sourceFile
    val message = new StringBuffer

    if (sourceFile == null) {
      message.append(MessageFormat.format("{0}", error.message))
    } else {
      var line: String = null
      var lineNum: String = null
      try {
        line = if (location != null && sourceFile != null) getLine(sourceFile, location.line) else ""
        lineNum = if (location != null) Integer.toString(location.line) else ""
      }
      catch {
        case e: IOException => {
          e.printStackTrace
        }
      }
      message.append(MessageFormat.format("{0}:{1}: {2}", sourceFile, lineNum, error.message))
      message.append(Systems.lineSeparator)
      message.append("\t\t")
      message.append(line)
      message.append(Systems.lineSeparator)
      message.append("\t\t")
      if (location != null)  message.append(getCursor(location.column))
    }
    System.err.println(new String(message))
  }

  private def getCursor(column: Int): String =  " " * (column - 1) + "^"

  private def getLine(sourceFile: String, lineNumber: Int): String = {
    try {
      val reader = Inputs.newReader(sourceFile)
      try {
        val line = Iterator.continually(reader.readLine()).takeWhile(_ != null).zipWithIndex.map { case (e, i) => (e, i + 1) }.find {
          case (e, i) => i == lineNumber
        }
        line.get._1
      } finally {
        reader.close
      }
    } catch {
      case e:FileNotFoundException => ""
    }
  }
}