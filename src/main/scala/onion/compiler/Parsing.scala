package onion.compiler

import collection.mutable.ArrayBuffer
import scala.jdk.CollectionConverters._
import java.io.{IOException, Reader, StringReader}
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
  private def syntaxErrorMessage(found: String, expected: String, context: String = "", sourceLine: String = ""): String = {
    // At EOF the expected-token list is a large, unhelpful dump; report the real
    // problem (an unclosed block/paren) instead.
    if (found == null || found.isEmpty) return Message("error.parsing.unexpected_eof")
    val base =
      if (found == "\"") Message("error.parsing.unterminated_string")
      else Message("error.parsing.syntax_error", displayTokenImage(found), expected)
    val hint = commonSyntaxHint(found, expected, context, sourceLine)
    if (hint.isEmpty) base else base + " " + hint
  }

  /**
   * A trailing lambda's parameter list is never parenthesized (`{ k, v -> ... }`,
   * unlike the non-trailing `(k, v) -> ...` form), so `{ (k, v) -> ... }` fails
   * right at the `{` with an unhelpful expected-token dump.
   */
  private val ParenthesizedTrailingLambdaHead = """\A\{\s*\(([^()]*)\)\s*->""".r

  /**
   * The trailing-lambda arrow used to be `=>`, and nothing else in the language ever was.
   * `=>` is no longer a token, so `{ x => ... }` fails at the `{` — the lookahead for a
   * trailing lambda no longer matches and the brace is left where a statement cannot
   * start. Without a hint the message is an expected-token dump that never mentions the
   * arrow, which is the one thing that is wrong.
   */
  private val OldArrowTrailingLambdaHead = """\A\{\s*(?:\(?[^(){}=]*\)?)\s*=>""".r

  /**
   * Add friendly hints for common syntax mistakes.
   */
  /**
   * A Java/JS/C-style `switch` statement at the start of a source line. `switch`
   * isn't a keyword in Onion -- it parses as a bare identifier-reference
   * statement -- so the parser actually trips on whatever follows it (the
   * condition, or the `{` of a parenthesized condition), never on `switch`
   * itself. Matching the *line*, not the token that triggered the error,
   * catches `switch x { ... }` and `switch (x) { ... }` alike.
   */
  private val LeadingSwitchStatement = """^\s*switch\b""".r

  /**
   * A Kotlin (`fun`), Swift/Go (`func`), or Rust (`fn`) function/method
   * declaration. None of these are keywords in Onion (which uses `def`), so at
   * statement position the keyword parses as a bare identifier reference and the
   * parser trips on the declaration name that follows, while inside a
   * class/interface body it trips on the keyword itself -- neither mentions
   * `def`. Matching the source line for `<kw> name(` catches both. A real call
   * to a method named `fun`/`func`/`fn` is `fun(...)` with no name between the
   * keyword and the paren, so it never matches; `fun name(` is not a valid Onion
   * expression anyway.
   */
  private val FunDeclaration = """\b(?:fun|func|fn)\s+[A-Za-z_]\w*\s*\(""".r

  /**
   * A Java-style parenthesized catch clause, `catch (e: Exception) { }`. `catch`
   * is a real keyword in Onion, but its variable is never parenthesized -- the
   * parser expects the binding name directly after `catch` and trips on the `(`.
   * `catch` cannot appear as an identifier (it is reserved), so `catch\s*\(`
   * anywhere on the line can only be this mistake.
   */
  private val ParenthesizedCatchClause = """\bcatch\s*\(""".r

  /**
   * A C/Java/JS-style parenthesized `for (init; cond; step)` loop. Onion's `for` is
   * a real keyword but its three clauses are never parenthesized, so the parser treats
   * the `(` as the start of a parenthesized initializer expression and trips several
   * tokens later -- on whatever follows the type name -- never mentioning that `for`
   * itself takes no parens. `for` cannot appear as an identifier (it is reserved), so
   * a leading `for\s*\(` on the line can only be this mistake.
   */
  private val CStyleForLoop = """^\s*for\s*\(""".r

  /**
   * A Java/C#-style unbraced `import java.util.List;` (or wildcard/no-semicolon variant).
   * Onion's `import` decl always wraps its targets in braces -- `import { java.util.List }`
   * -- so the parser consumes the `import` keyword and then trips on the identifier that
   * follows, expecting `{`. `import` cannot appear as an identifier (it is reserved), so a
   * leading `import\s+` followed directly by a non-`{` on the line can only be this mistake.
   */
  private val JavaStyleImport = """^\s*import\s+(?!\{)\S""".r

  /**
   * A Java-style constructor, named after the class instead of using `def this`:
   * `public ClassName(...) { }`, or the same with no access modifier. `public`/
   * `private`/`protected` are real keywords (access-section markers), so with a
   * modifier the parser consumes it and trips on the class name, expecting `:` --
   * without one, a bare capitalized identifier at member position trips on itself,
   * expecting a member declarator (`def`, `val`, `var`, ...). Neither mentions
   * `def this`. Requiring a capitalized identifier (Java/Onion class-naming
   * convention) directly followed by `(` keeps this from firing on an ordinary
   * lowercase-named call or declaration.
   */
  private val JavaStyleConstructor = """^\s*(?:public|private|protected)?\s*[A-Z][A-Za-z0-9_]*\s*\(""".r

  /**
   * A Java-style method declaration, `public void method() { }` (or `private`/`protected`,
   * with any return-type spelling before the name). `public`/`private`/`protected` are
   * access-*section* markers that only ever appear as `public:`, immediately followed by a
   * colon -- every member's own return type instead comes after its name via `def`. The
   * parser consumes the modifier and then trips on the return-type identifier that follows,
   * expecting `:` -- never mentioning `def`. Requiring two identifiers (return type, then
   * name) before the `(` keeps this from firing on the single-identifier
   * `public ClassName(...)` constructor mistake, which gets its own hint.
   */
  private val JavaStyleMethodDeclaration = """^\s*(?:public|private|protected)\s+[A-Za-z_]\w*\s+[A-Za-z_]\w*\s*\(""".r

  /**
   * A Java/Scala-style type-pattern `select` case, `case s: String:` (or with extra
   * spacing, `case s : String :`). Onion's `case` patterns use `is` for a type test
   * (`case s is String:`) -- a colon straight after the binding name is never valid
   * there, so the parser reads `s` alone as an ordinary value pattern (an
   * ExpressionPattern) and only trips on the *second* colon that was meant to
   * introduce the type, several characters later on the same line -- never
   * mentioning `is`. Requiring a lower-case binding name (Onion's variable-naming
   * convention) before the first colon and a capitalized type name before the
   * second keeps this from firing on an ordinary multi-value case like `case 1, 2:`.
   */
  private val JavaStyleTypeCase = """^\s*case\s+[a-z_]\w*\s*:\s*[A-Z][\w.\[\]]*\??\s*:""".r

  private def commonSyntaxHint(found: String, expected: String, context: String, sourceLine: String): String = found match {
    // The symbolic spellings of inheritance, replaced by `extends` and `conforms`.
    // `<:` no longer appears in any production, so meeting one can only be old source.
    case "<:" =>
      Message("error.parsing.hint.old_conforms")
    case ":" if JavaStyleTypeCase.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.case_type_colon")
    case ":" if expected.contains("extends") =>
      Message("error.parsing.hint.old_extends")
    // The removed anonymous super-call form: `def this(x) : (x) { }`. After the colon the
    // parser now expects only `this`, so that expected-set is the discriminator -- checking
    // for `super` or `(` alone would fire at every expression position.
    case "(" if expected.contains("\"this\"") && !expected.contains("<ID>") =>
      Message("error.parsing.hint.old_super_init")
    case "(" if expected.contains("<ID>") && ParenthesizedCatchClause.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.catch_parens")
    case "in" =>
      Message("error.parsing.hint.old_for_in")
    case _ if LeadingSwitchStatement.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.switch_not_supported")
    case _ if FunDeclaration.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.fun_declaration")
    case _ if CStyleForLoop.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.c_style_for")
    case _ if JavaStyleImport.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.java_style_import")
    case _ if expected == "\":\"" && JavaStyleMethodDeclaration.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.java_style_method")
    case _ if (expected == "\":\"" || expected.contains("\"def\"")) && JavaStyleConstructor.findFirstMatchIn(sourceLine).isDefined =>
      Message("error.parsing.hint.java_style_constructor")
    // There is no `cond ? a : b` ternary operator -- `if`/`else` covers the same
    // ground as an expression, so unlike the hints above this isn't a token swap;
    // name the rewrite so the reader isn't left guessing what "unsupported" means here.
    case "?" =>
      Message("error.parsing.hint.ternary")
    case "else" =>
      Message("error.parsing.hint.dangling_else")
    // Checked before the paren hint: `{ (k, v) => ... }` is wrong twice, and the arrow
    // is the part the writer will not spot on their own.
    case "{" if OldArrowTrailingLambdaHead.findFirstMatchIn(context).isDefined =>
      Message("error.parsing.hint.old_trailing_arrow")
    case "{" if ParenthesizedTrailingLambdaHead.findFirstMatchIn(context).isDefined =>
      val params = ParenthesizedTrailingLambdaHead.findFirstMatchIn(context).get.group(1).trim
      Message("error.parsing.hint.trailing_lambda_parens", params)
    case _ if expected.contains("{") && !Set(";", "<EOL>", "<EOF>").exists(expected.contains) =>
      Message("error.parsing.hint.block_expected")
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
