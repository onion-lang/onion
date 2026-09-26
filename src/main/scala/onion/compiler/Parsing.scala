package onion.compiler

import collection.mutable.ArrayBuffer
import java.io.{IOException}

import _root_.onion.compiler.toolbox.Message
import _root_.onion.compiler.exceptions.CompilationException
import _root_.onion.compiler.parser.{
  OnionLexer,
  OnionParser,
  ExpectedTokenFormatter,
  JJOnionParser,
  JJOnionParserConstants,
  ParseException,
  SourceContext,
  SyntaxHint,
  SyntaxHintClassifier
}

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
  private def stripShebang(text: String): String = {
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
      val sourceText = stripShebang(source.readText())

      // Fast path: the handwritten parser. It produces the same AST as the JavaCC parser
      // for every program the latter accepts, and gives up (Fail) on anything else, in which
      // case a second, recovery-mode pass of the same parser owns the diagnostics: it
      // collects every syntax error (resynchronizing at top-level declarations between
      // them) instead of stopping at the first.
      if (!Parsing.forceJavaCC) {
        val fast = try OnionParser.parse(sourceText) catch { case _: OnionParser.Fail => null }
        if (fast != null) {
          units += fast.copy(sourceFile = source.name)
          return
        }

        val parser = new OnionParser(sourceText)
        parser.enableErrorRecovery(maxErrorsPerFile)
        val unit = parser.unit().copy(sourceFile = source.name)
        if (parser.hasErrors) collectFastParseErrors(parser, source.name, sourceText, problems)
        else units += unit // the first pass's give-up was spurious; keep the good parse
        return
      }

      // -Donion.parser.javacc=true: the old all-JavaCC flow, kept as a kill-switch and as
      // the differential-testing oracle for the recovery port above.
      val parser = new JJOnionParser(new OnionLexer(sourceText))

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
      }
    } catch {
      case _: IOException =>
        problems += CompileError(null, null, Message("error.parsing.read_error", source.name))
      case e: Error if !e.isInstanceOf[VirtualMachineError] =>
        // The lexer throws java.lang.Error for invalid source (e.g. a truncated \uXXXX
        // escape). Catch it here so it surfaces as a parse error instead of I0000.
        val msg = if (e.getMessage != null) e.getMessage else "invalid source"
        problems += CompileError(source.name, new Location(1, 1), msg)
    }
  }

  /**
   * Collect errors from the handwritten parser's error recovery buffer. The expected-kind
   * sets render through the same token-image table and formatter as the JavaCC path, so the
   * message text and the hint classification are identical for the same diagnostic.
   */
  private def collectFastParseErrors(
    parser: OnionParser,
    fileName: String,
    sourceText: String,
    problems: ArrayBuffer[CompileError]
  ): Unit = {
    val images = JJOnionParserConstants.tokenImage
    for (error <- parser.getCollectedErrors) {
      val expected = ExpectedTokenFormatter.formatKinds(error.expectedKinds, images)
      val expectedAll = ExpectedTokenFormatter.formatAllKinds(error.expectedKinds, images)
      val sourceContext = SourceContext.at(sourceText, error.line, error.column)
      problems += CompileError(
        fileName,
        new Location(error.line, error.column),
        syntaxErrorMessage(
          error.found,
          expected,
          sourceContext.context,
          sourceContext.sourceLine,
          expectedAll
        )
      )
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
      val sourceContext = SourceContext.at(sourceText, error.line, error.column)
      problems += CompileError(
        fileName,
        new Location(error.line, error.column),
        syntaxErrorMessage(
          error.found,
          error.expected,
          sourceContext.context,
          sourceContext.sourceLine,
          error.expectedAll
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
      val expected = ExpectedTokenFormatter.format(e.expectedTokenSequences, e.tokenImage)
      val expectedAll = ExpectedTokenFormatter.formatAll(e.expectedTokenSequences, e.tokenImage)
      val sourceContext = SourceContext.at(sourceText, error.beginLine, error.beginColumn)
      problems += CompileError(
        fileName,
        new Location(error.beginLine, error.beginColumn),
        syntaxErrorMessage(
          error.image,
          expected,
          sourceContext.context,
          sourceContext.sourceLine,
          expectedAll
        )
      )
    }
  }

  /**
   * A lone double quote can only come from an unterminated string literal
   * (complete strings lex as a single STRING token); report it as such
   * instead of listing unrelated expected tokens.
   */
  private def syntaxErrorMessage(found: String, expected: String, context: String, sourceLine: String, expectedAll: String): String = {
    // At EOF the expected-token list is a large, unhelpful dump; report the real
    // problem (an unclosed block/paren) instead.
    if (found == null || found.isEmpty) return Message("error.parsing.unexpected_eof")
    val base =
      if (found == "\"") Message("error.parsing.unterminated_string")
      else Message("error.parsing.syntax_error", displayTokenImage(found), expected)
    val hint = SyntaxHintClassifier
      .classify(found, if (expectedAll == null) expected else expectedAll, context, sourceLine)
      .map(renderSyntaxHint)
      .getOrElse("")
    if (hint.isEmpty) base else base + " " + hint
  }

  private def renderSyntaxHint(hint: SyntaxHint): String =
    hint.arguments match {
      case Seq() => Message(hint.messageKey)
      case Seq(argument) => Message(hint.messageKey, argument)
      case Seq(first, second) => Message(hint.messageKey, first, second)
      case more => Message(hint.messageKey, more.toArray[Any])
    }

  /** Make control characters and EOF visible in error messages. */
  private def displayTokenImage(image: String): String =
    if (image == null || image.isEmpty) "<EOF>"
    else image.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

}

object Parsing {
  /** `-Donion.parser.javacc=true` disables the handwritten fast path (for A/B tests and triage). */
  private[compiler] val forceJavaCC: Boolean = java.lang.Boolean.getBoolean("onion.parser.javacc")
}
