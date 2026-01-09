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
        val columnNumber = locationOpt.map(_.column).getOrElse(0)
        val locationText = (lineNumber, columnNumber) match {
          case (l, c) if l > 0 && c > 0 => s"$l:$c"
          case (l, _) if l > 0 => l.toString
          case _ => ""
        }
        val lineText = locationOpt.flatMap(loc => readSourceLine(sourceFile, loc.line)).getOrElse("")

        // Header: file:line:column: [ECODE] message
        builder.append(s"$sourceFile:$locationText: $errorCodePrefix${error.message}")
        builder.append(Systems.lineSeparator)

        // Source line with line number prefix
        if (lineText.nonEmpty && lineNumber > 0) {
          val lineNumWidth = lineNumber.toString.length
          val prefix = s"  $lineNumber | "
          builder.append(prefix)
          builder.append(lineText)
          builder.append(Systems.lineSeparator)

          // Underline for error span
          locationOpt.foreach { loc =>
            val indentPrefix = " " * (lineNumWidth + 2) + " | "
            builder.append(indentPrefix)
            builder.append(underlineAt(loc))
          }
        }
    }
    builder.toString
  }

  private def underlineAt(loc: Location): String = {
    val safeColumn = max(loc.column, 1)
    val underlineLength = loc.spanLength
    val spaces = " " * (safeColumn - 1)
    if (underlineLength > 1) {
      spaces + "~" * underlineLength
    } else {
      spaces + "^"
    }
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
