package onion.compiler

import collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import java.io.{IOException, Reader}
import java.util.Arrays.ArrayList
import java.util.Collections

import _root_.onion.compiler.toolbox.Message
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.parser.{JJOnionParser, ParseException}

/**
 * Parsing phase of the Onion compiler.
 *
 * Features:
 * - Collects multiple syntax errors per file (up to maxErrorsPerFile)
 * - Continues parsing after errors to find more issues
 * - Provides better error messages with expected token information
 */
class Parsing(config: CompilerConfig) extends AnyRef
  with Processor[Seq[InputSource], Seq[AST.CompilationUnit]] {

  /** Maximum number of syntax errors to collect per file before stopping */
  private val maxErrorsPerFile = config.maxErrorReports

  type Environment = Null

  def newEnvironment(source: Seq[InputSource]): Null = null

  def processBody(source: Seq[InputSource], environment: Null): Seq[AST.CompilationUnit] = {
    val buffer = new ArrayBuffer[AST.CompilationUnit]()
    val problems = new ArrayBuffer[CompileError]()

    for (i <- source.indices) {
      parseFile(source(i), buffer, problems)
    }

    if (problems.nonEmpty) throw new CompilationException(problems.toSeq)
    buffer.toSeq
  }

  /**
   * Parse a single source file and collect any errors.
   *
   * Uses error recovery mode to collect multiple syntax errors per file
   * when possible.
   */
  private def parseFile(
    source: InputSource,
    units: ArrayBuffer[AST.CompilationUnit],
    problems: ArrayBuffer[CompileError]
  ): Unit = {
    try {
      val reader = source.openReader()
      val parser = new JJOnionParser(reader)

      // Enable error recovery mode to collect multiple errors
      parser.enableErrorRecovery(maxErrorsPerFile)

      try {
        val unit = parser.unit().copy(sourceFile = source.name)

        // Check for collected errors during parsing
        if (parser.hasErrors()) {
          collectParseErrors(parser, source.name, problems)
        }

        // Only add the unit if we got a valid result
        if (!parser.hasErrors()) {
          units += unit
        }
      } catch {
        case e: ParseException =>
          // First, add any collected errors
          if (parser.hasErrors()) {
            collectParseErrors(parser, source.name, problems)
          }
          // Then add the final error that stopped parsing
          addParseException(e, source.name, problems)
      } finally {
        reader.close()
      }
    } catch {
      case e: IOException =>
        problems += CompileError(null, null, Message("error.parsing.read_error", source.name))
    }
  }

  /**
   * Collect errors from the parser's error recovery buffer.
   */
  private def collectParseErrors(
    parser: JJOnionParser,
    fileName: String,
    problems: ArrayBuffer[CompileError]
  ): Unit = {
    val errors = parser.getCollectedErrors()
    for (i <- 0 until errors.size()) {
      val error = errors.get(i)
      problems += CompileError(
        fileName,
        new Location(error.line, error.column),
        syntaxErrorMessage(error.found, error.expected)
      )
    }
  }

  /**
   * Add a ParseException to the problems list.
   */
  private def addParseException(
    e: ParseException,
    fileName: String,
    problems: ArrayBuffer[CompileError]
  ): Unit = {
    // Message-only ParseExceptions (e.g. from string-interpolation splitting)
    // carry no token information; report their message at an unknown location.
    if (e.currentToken == null) {
      problems += CompileError(fileName, new Location(1, 1), e.getMessage)
    } else {
      val error = e.currentToken.next
      val expected = formatExpectedTokens(e)
      problems += CompileError(
        fileName,
        new Location(error.beginLine, error.beginColumn),
        syntaxErrorMessage(error.image, expected)
      )
    }
  }

  /**
   * Format expected tokens from a ParseException for better error messages.
   *
   * When multiple tokens are expected, this creates a more readable message
   * like "';' or 'newline'" instead of just showing the first one.
   */
  /**
   * A lone double quote can only come from an unterminated string literal
   * (complete strings lex as a single STRING token); report it as such
   * instead of listing unrelated expected tokens.
   */
  private def syntaxErrorMessage(found: String, expected: String): String = {
    val base =
      if (found == "\"") Message("error.parsing.unterminated_string")
      else Message("error.parsing.syntax_error", displayTokenImage(found), expected)
    val hint = commonSyntaxHint(found, expected)
    if (hint.isEmpty) base else base + " " + hint
  }

  /**
   * Add friendly hints for common syntax mistakes.
   */
  private def commonSyntaxHint(found: String, expected: String): String = found match {
    case "in" =>
      "Hint: Onion does not support `for x in xs`. Use a C-style loop: `for var i = 0; i < xs.size(); i = i + 1 { ... }`."
    case "else" =>
      "Hint: `else` must follow an `if` block. Onion does not support `if` as an expression; use `if (cond) { ... } else { ... }`."
    case _ if expected.contains("{") && !Set(";", "<EOL>", "<EOF>").exists(expected.contains) =>
      "Hint: a block `{ ... }` is expected here."
    case _ =>
      ""
  }

  /** Make control characters and EOF visible in error messages. */
  private def displayTokenImage(image: String): String =
    if (image == null || image.isEmpty) "<EOF>"
    else image.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

  private def formatExpectedTokens(e: ParseException): String = {
    val sequences = e.expectedTokenSequences
    if (sequences == null || sequences.isEmpty) {
      return "valid token"
    }

    // Collect unique expected tokens
    val expectedSet = scala.collection.mutable.LinkedHashSet[String]()
    for (seq <- sequences) {
      if (seq != null && seq.nonEmpty) {
        expectedSet += e.tokenImage(seq(0))
      }
    }

    val expected = expectedSet.toSeq
    if (expected.isEmpty) {
      "valid token"
    } else if (expected.size == 1) {
      expected.head
    } else if (expected.size <= 3) {
      // Show all expected tokens if 3 or fewer
      expected.init.mkString(", ") + " or " + expected.last
    } else {
      val shown = expected.take(4)
      shown.mkString(", ") + s", ... (${expected.size - shown.size} more)"
    }
  }
}
