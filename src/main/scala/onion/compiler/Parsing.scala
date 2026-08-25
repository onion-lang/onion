package onion.compiler

import collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import java.io.{IOException, Reader, StringReader}

import _root_.onion.compiler.toolbox.Message
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.parser.{JJOnionParser, ParseException, SyntaxHint, SyntaxHintClassifier}

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
  /**
   * A `#!` shebang is only meaningful on the very first line of a script. Drop
   * the first line's content when it starts with `#!`, keeping the newline so
   * every other line keeps its original line number. On any other line `#!` is
   * left for the lexer to reject rather than being silently skipped (issue #262).
   */
  private def stripShebang(reader: Reader): String = {
    val sb = new StringBuilder
    val buf = new Array[Char](4096)
    try {
      var n = reader.read(buf)
      while (n != -1) { sb.appendAll(buf, 0, n); n = reader.read(buf) }
    } finally reader.close()
    val text = sb.toString
    if (text.startsWith("#!")) {
      val nl = text.indexOf('\n')
      if (nl < 0) "" else text.substring(nl)
    } else text
  }

  private def parseFile(
    source: InputSource,
    units: ArrayBuffer[AST.CompilationUnit],
    problems: ArrayBuffer[CompileError]
  ): Unit = {
    try {
      val sourceText = stripShebang(source.openReader())
      val reader = new StringReader(sourceText)
      val parser = new JJOnionParser(reader)

      // Enable error recovery mode to collect multiple errors
      parser.enableErrorRecovery(maxErrorsPerFile)

      try {
        val unit = parser.unit().copy(sourceFile = source.name)

        // Check for collected errors during parsing
        if (parser.hasErrors()) {
          collectParseErrors(parser, source.name, sourceText, problems)
        }

        // Only add the unit if we got a valid result
        if (!parser.hasErrors()) {
          units += unit
        }
      } catch {
        case e: ParseException =>
          // First, add any collected errors
          if (parser.hasErrors()) {
            collectParseErrors(parser, source.name, sourceText, problems)
          }
          // Then add the final error that stopped parsing
          addParseException(e, source.name, sourceText, problems)
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
    sourceText: String,
    problems: ArrayBuffer[CompileError]
  ): Unit = {
    val errors = parser.getCollectedErrors()
    for (i <- 0 until errors.size()) {
      val error = errors.get(i)
      problems += CompileError(
        fileName,
        new Location(error.line, error.column),
        syntaxErrorMessage(
          error.found,
          error.expected,
          contextAt(sourceText, error.line, error.column),
          sourceLineAt(sourceText, error.line)
        )
      )
    }
  }

  /**
   * Add a ParseException to the problems list.
   */
  private def addParseException(
    e: ParseException,
    fileName: String,
    sourceText: String,
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
        syntaxErrorMessage(
          error.image,
          expected,
          contextAt(sourceText, error.beginLine, error.beginColumn),
          sourceLineAt(sourceText, error.beginLine)
        )
      )
    }
  }

  /**
   * The source text starting at a 1-based (line, column), for hints that need
   * to look past the offending token (bounded so a pathological file can't
   * make the regex match slow).
   */
  private def lineStartOffset(sourceText: String, line: Int): Int = {
    var offset = 0
    var currentLine = 1
    while (currentLine < line && offset >= 0 && offset < sourceText.length) {
      val nl = sourceText.indexOf('\n', offset)
      if (nl < 0) offset = sourceText.length else { offset = nl + 1; currentLine += 1 }
    }
    offset
  }

  private def contextAt(sourceText: String, line: Int, column: Int): String = {
    val start = math.min(sourceText.length, lineStartOffset(sourceText, line) + column - 1)
    sourceText.substring(start, math.min(sourceText.length, start + 200))
  }

  /**
   * The full text of the 1-based source line containing (line, column), for
   * hints that need to recognize a whole malformed statement (e.g. a leading
   * `switch`) rather than just the token at the error position -- the error
   * itself is often reported several tokens past the actual mistake.
   */
  private def sourceLineAt(sourceText: String, line: Int): String = {
    val start = lineStartOffset(sourceText, line)
    val end = sourceText.indexOf('\n', start) match {
      case -1 => sourceText.length
      case i => i
    }
    sourceText.substring(start, end)
  }

  /**
   * A lone double quote can only come from an unterminated string literal
   * (complete strings lex as a single STRING token); report it as such
   * instead of listing unrelated expected tokens.
   */
  private def syntaxErrorMessage(found: String, expected: String, context: String = "", sourceLine: String = ""): String = {
    // At EOF the expected-token list is a large, unhelpful dump; report the real
    // problem (an unclosed block/paren) instead.
    if (found == null || found.isEmpty) return Message("error.parsing.unexpected_eof")
    val base =
      if (found == "\"") Message("error.parsing.unterminated_string")
      else Message("error.parsing.syntax_error", displayTokenImage(found), expected)
    val hint = SyntaxHintClassifier
      .classify(found, expected, context, sourceLine)
      .map(renderSyntaxHint)
      .getOrElse("")
    if (hint.isEmpty) base else base + " " + hint
  }

  private def renderSyntaxHint(hint: SyntaxHint): String =
    hint.arguments match {
      case Seq() => Message(hint.messageKey)
      case Seq(argument) => Message(hint.messageKey, argument)
      case Seq(first, second) => Message(hint.messageKey, first, second)
      case _ => throw new IllegalArgumentException(s"Unsupported syntax-hint arity: ${hint.arguments.size}")
    }

  /** Make control characters and EOF visible in error messages. */
  private def displayTokenImage(image: String): String =
    if (image == null || image.isEmpty) "<EOF>"
    else image.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

  /**
   * Format expected tokens from a ParseException for better error messages.
   *
   * When multiple tokens are expected, this creates a more readable message
   * like "';' or 'newline'" instead of just showing the first one.
   */
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
