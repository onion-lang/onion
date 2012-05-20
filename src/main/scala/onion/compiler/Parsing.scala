package onion.compiler

import collection.mutable.ArrayBuffer
import collection.JavaConversions._
import java.io.{Reader, IOException}
import _root_.onion.compiler.util.Messages
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.parser.{JJOnionParser, Token, ParseException}
import onion.compiler.parser.JJOnionParser

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 2010/12/05
 * Time: 23:55:48
 * To change this template use File | Settings | File Templates.
 */

class Parsing(config: CompilerConfig) extends AnyRef
  with ProcessingUnit[Array[InputSource], Array[AST.CompilationUnit]] {
  type Environment = Null
  def newEnvironment(source: Array[InputSource]): Null = null
  def doProcess(source: Array[InputSource], environment: Null): Array[AST.CompilationUnit] = {
    def parse(reader: Reader, fileName: String): AST.CompilationUnit = {
      new JJOnionParser(reader).unit().copy(sourceFile = fileName)
    }
    val buffer = new ArrayBuffer[AST.CompilationUnit]()
    val problems = new ArrayBuffer[CompileError]()
    for(i <- 0 until source.length) {
      try {
        buffer += parse(source(i).openReader, source(i).getName)
      } catch {
        case e: IOException =>
          problems += new CompileError(null, null, Messages.get("error.parsing.read_error", source(i).getName))
        case e: ParseException =>
          val error = e.currentToken.next
          val expected = e.tokenImage(e.expectedTokenSequences(0)(0))
          problems += new CompileError(source(i).getName, new Location(error.beginLine, error.beginColumn), Messages.get("error.parsing.syntax_error", error.image, expected))
      }
    }
    if(problems.length > 0) throw new CompilationException(problems)
    buffer.toArray
  }

}
