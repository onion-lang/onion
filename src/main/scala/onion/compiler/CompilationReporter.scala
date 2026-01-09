/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

import onion.compiler.toolbox.{Inputs, Message, Systems}

import java.io.FileNotFoundException
import java.io.IOException
import scala.math.max
import scala.util.Using

/**
 * Utility for printing compiler diagnostics in a consistent way.
 */
object CompilationReporter {

  def printErrors(errors: Seq[CompileError]): Unit = {
    if (errors.isEmpty) return
    errors.map(formatError).foreach(System.err.println)
    System.err.println(Message("error.count", errors.size))
  }

  private def formatError(error: CompileError): String = {
    val locationOpt = Option(error.location)
    val sourceFileOpt = Option(error.sourceFile)
    val errorCodePrefix = error.errorCode.map(code => s"[$code] ").getOrElse("")
    val builder = new StringBuilder
    sourceFileOpt match {
      case None =>
        builder.append(s"$errorCodePrefix${error.message}")
      case Some(sourceFile) =>
        val lineNumber = locationOpt.map(_.line).getOrElse(0)
        val lineNumText = if (lineNumber > 0) Integer.toString(lineNumber) else ""
        val lineText = locationOpt.flatMap(loc => readSourceLine(sourceFile, loc.line)).getOrElse("")

        builder.append(s"$sourceFile:$lineNumText: $errorCodePrefix${error.message}")
        builder.append(Systems.lineSeparator)
        builder.append("\t\t")
        builder.append(lineText)
        builder.append(Systems.lineSeparator)
        locationOpt.foreach { loc =>
          builder.append("\t\t")
          builder.append(cursorAt(loc.column))
        }
    }
    builder.toString
  }

  private def cursorAt(column: Int): String = {
    val safeColumn = max(column, 1)
    " " * (safeColumn - 1) + "^"
  }

  private def readSourceLine(sourceFile: String, lineNumber: Int): Option[String] = {
    if (lineNumber <= 0) return None
    Using(Inputs.newReader(sourceFile)) { reader =>
      Iterator
        .continually(reader.readLine())
        .takeWhile(_ != null)
        .zipWithIndex
        .collectFirst { case (line, index) if index + 1 == lineNumber => line }
    }.recover {
      case _: FileNotFoundException | _: IOException => None
    }.getOrElse(None)
  }
}
