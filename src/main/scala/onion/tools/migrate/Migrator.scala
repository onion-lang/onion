package onion.tools.migrate

import onion.compiler.parser.{JJOnionParserConstants as K, OnionLexer, Token}

import scala.collection.mutable

/**
 * The rewrite rules behind `onion migrate`: one per pre-1.0 breaking change.
 *
 * A rule works on the token stream, never on raw text, so a string or a comment that happens
 * to contain the old spelling is never touched. Everything outside the rewritten tokens is
 * copied byte for byte, and the result is re-lexed and compared with the original before
 * anything is written (see [[MigrateCommand]]).
 *
 * A rule finds the places that look like the old spelling. Whether one really is the old
 * spelling depends on names the rule cannot resolve, so [[MigrateCommand]] decides by
 * compiling: a file that compiles is never changed, and a rewrite is kept only if the
 * rewritten file compiles.
 */
object Migrator:

  /** One rewrite: `length` characters at `offset` become `replacement`. */
  final case class Edit(offset: Int, length: Int, replacement: String, line: Int, column: Int, note: String)

  /** A place a rule left alone, or could not change, with why. */
  final case class Note(line: Int, column: Int, message: String)

  /**
   * `text` is the source with every edit applied. `notes` are places a rule proved are not
   * the old spelling; they are reported, but need nothing from the user.
   */
  final case class Result(text: String, edits: Seq[Edit], notes: Seq[Note]):
    def changed: Boolean = edits.nonEmpty

  def migrate(source: String): Result =
    val (last, notLast) = RetToYield.find(source).partition(_._2)
    val notes = notLast.map { (offset, _) =>
      val (line, column) = lineColumnOf(source, offset)
      Note(line, column,
        "this `ret` is not the last clause of its do block, so it is a call through a name " +
          "`ret` declared elsewhere, not the old keyword; left as written")
    }
    val edits = last.map { (offset, _) =>
      val (line, column) = lineColumnOf(source, offset)
      Edit(offset, 3, "yield", line, column, "ret -> yield")
    }
    Result(apply(source, edits), edits, notes)

  /**
   * The token images of `source`, comments included, for the before/after comparison: a
   * migration may change exactly the tokens it rewrote and nothing else.
   */
  def signature(source: String): Seq[String] =
    val images = mutable.Buffer[String]()
    val lexer = new OnionLexer(source)
    var token = lexer.getNextToken()
    while
      val comments = mutable.Buffer[String]()
      var special = token.specialToken
      while special != null do
        comments.prepend(special.image)
        special = special.specialToken
      images ++= comments
      if token.kind != K.EOF then images += token.image
      token.kind != K.EOF
    do token = lexer.getNextToken()
    images.toSeq

  private def apply(source: String, edits: Seq[Edit]): String =
    val out = new StringBuilder
    var cursor = 0
    edits.sortBy(_.offset).foreach { edit =>
      out ++= source.substring(cursor, edit.offset)
      out ++= edit.replacement
      cursor = edit.offset + edit.length
    }
    out ++= source.substring(cursor)
    out.toString

  /** The code tokens of `source`, without comments, in order. */
  private def codeTokens(source: String): IndexedSeq[Token] =
    val tokens = mutable.ArrayBuffer[Token]()
    val lexer = new OnionLexer(source)
    var token = lexer.getNextToken()
    while token.kind != K.EOF do
      tokens += token
      token = lexer.getNextToken()
    tokens.toIndexedSeq

  /**
   * The character offset of a token's position. The lexer counts a tab to the next multiple
   * of eight, as JavaCC does, so the column is not a character index; this walks the line
   * with the same rule. `None` when no character sits at that column (a `\u` escape shifts
   * the lexer's columns), and the caller then leaves the token alone.
   */
  private[migrate] def offsetOf(source: String, line: Int, column: Int): Option[Int] =
    var start = 0
    var current = 1
    while current < line && start >= 0 do
      start = source.indexOf('\n', start)
      if start >= 0 then start += 1
      current += 1
    if start < 0 then None
    else
      var i = start
      var col = 0
      var found: Option[Int] = None
      while found.isEmpty && i < source.length && source.charAt(i) != '\n' do
        col += 1
        if source.charAt(i) == '\t' then
          col -= 1
          col += 8 - (col % 8)
        if col == column then found = Some(i)
        i += 1
      found

  /** The lexer's line and column of the character at `offset`: the inverse of [[offsetOf]]. */
  private[migrate] def lineColumnOf(source: String, offset: Int): (Int, Int) =
    val lineStart = source.lastIndexOf('\n', offset - 1) + 1
    val line = 1 + (0 until lineStart).count(source.charAt(_) == '\n')
    var column = 0
    (lineStart to offset).foreach { i =>
      column += 1
      if source.charAt(i) == '\t' && i < offset then
        column -= 1
        column += 8 - (column % 8)
    }
    (line, column)

  /**
   * The code inside each `#{...}` of a string literal token, as (offset in the token's
   * image, text). A single-quoted string skips a `#{` behind an odd number of backslashes,
   * which is literal text; a multi-line string has no escapes. Braces are matched the way
   * the parser does, skipping string literals inside the body.
   */
  private def interpolationBodies(token: Token): Seq[(Int, String)] =
    val single = token.kind == K.STRING
    if !single && token.kind != K.MULTI_LINE_STRING then return Seq.empty
    val image = token.image
    val quote = if single then 1 else 3
    val end = image.length - quote
    val bodies = mutable.Buffer[(Int, String)]()
    var from = quote
    var going = true
    while going do
      var start = image.indexOf("#{", from)
      if single then
        while start >= 0 && start < end && {
          var slashes = 0
          while image.charAt(start - 1 - slashes) == '\\' do slashes += 1
          slashes % 2 == 1
        } do start = image.indexOf("#{", start + 1)
      if start < 0 || start >= end then going = false
      else
        var depth = 1
        var i = start + 2
        while i < end && depth > 0 do
          image.charAt(i) match
            case '"' =>
              i += 1
              while i < end && image.charAt(i) != '"' do
                if image.charAt(i) == '\\' then i += 1
                i += 1
            case '{' => depth += 1
            case '}' => depth -= 1
            case _ => ()
          i += 1
        if depth > 0 then going = false
        else
          bodies += ((start + 2, image.substring(start + 2, i - 1)))
          from = i
    bodies.toSeq

  /**
   * `ret e` → `yield e` in a `do[M] { ... }` block (#2291).
   *
   * Before the change `ret` was a keyword, legal only at the start of a do block's final
   * clause, followed by that clause's expression. Every `ret` in that place is a candidate.
   * Today `ret` is an ordinary name, and `ret a` there may also be a name `ret` declared
   * somewhere this file cannot see (an inherited method or field, another file), followed by
   * a clause `a`. Only the compiler can tell, so [[MigrateCommand]] keeps a candidate only
   * when the file does not compile as written and does compile with it rewritten.
   *
   * A `ret` with another clause after it was never the keyword: it is noted and left alone.
   * The full rule is on #2442.
   */
  private object RetToYield:
    /** After these, `ret` is an operand, not the start of a clause. */
    private val NotAnExpression = Set(
      ".", "?.", "::", "=", "<-", ":", "->", ")", "]", "}", ";", ",", "++", "--",
      "*", "/", "%", "==", "!=", "===", "!==", "<", ">", "<=", ">=", "&&", "||", "?:", "|>",
      "+=", "-=", "*=", "/=", "%=", "as", "is", "instanceof")
    private val ValueKeywords = Set("true", "false", "null", "this", "self", "super")
    /** On a new line, these continue the expression before them instead of starting a clause. */
    private val Continuations = Set(".", "?.", "|>", "else", "catch", "finally")
    /** At the end of a line, these leave the expression open. */
    private val OpenEnds = Set(
      "=", "+", "-", "*", "/", "%", "==", "!=", "===", "!==", "<", ">", "<=", ">=", "&&", "||",
      "?:", "|>", ".", "?.", "::", "->", ",", "as", "is", "instanceof", "!")

    /** Keywords that start an expression, so after a finished operand they start a new clause. */
    private val ExpressionKeywords = Set("new", "if", "select", "do", "try")

    private enum Bracket:
      case Paren, Square, Brace, DoBrace

    /**
     * Every candidate in `source` as (offset, whether it ends its do block), including those
     * in the code of a string's `#{...}`, which the lexer keeps inside one string token.
     */
    def find(source: String): Seq[(Int, Boolean)] =
      val tokens =
        try codeTokens(source)
        catch case _: Exception => IndexedSeq.empty
      val interpolated = tokens.flatMap { token =>
        val bodies = interpolationBodies(token)
        if bodies.isEmpty then Seq.empty
        else
          offsetOf(source, token.beginLine, token.beginColumn)
            .filter(offset => source.startsWith(token.image, offset))
            .toSeq
            .flatMap { offset =>
              bodies.flatMap { (start, body) =>
                find(body).map((at, last) => (offset + start + at, last))
              }
            }
      }
      (inThisCode(source, tokens) ++ interpolated).sortBy(_._1)

    private def inThisCode(source: String, tokens: IndexedSeq[Token]): Seq[(Int, Boolean)] =
      val candidates = mutable.Buffer[(Token, Int)]()
      val stack = mutable.Stack[Bracket]()
      // The `[` that opens a `do[...]`'s type, by stack depth, so its `]` knows that the
      // `{` after it opens a do block.
      val doTypes = mutable.Set[Int]()
      var nextBraceIsDo = false

      tokens.indices.foreach { i =>
        val token = tokens(i)
        val previous = if i > 0 then Some(tokens(i - 1)) else None
        val next = if i + 1 < tokens.length then Some(tokens(i + 1)) else None
        token.image match
          case "(" => stack.push(Bracket.Paren)
          case "[" =>
            if previous.exists(_.kind == K.K_DO) then doTypes += stack.size
            stack.push(Bracket.Square)
          case "{" =>
            stack.push(if nextBraceIsDo then Bracket.DoBrace else Bracket.Brace)
          case ")" | "]" | "}" =>
            if stack.nonEmpty then stack.pop()
            if token.image == "]" && doTypes.remove(stack.size) then
              nextBraceIsDo = next.exists(_.image == "{")
          case "ret" if token.kind == K.ID =>
            if stack.headOption.contains(Bracket.DoBrace) && startsClause(previous) && takesOperand(next) then
              candidates += ((token, i))
          case _ => ()
        if token.image == "{" then nextBraceIsDo = false
      }

      val located = candidates.toSeq.flatMap { (token, index) =>
        offsetOf(source, token.beginLine, token.beginColumn)
          .filter(offset => source.startsWith("ret", offset))
          .map(offset => (token, index, offset))
      }
      located.map((_, index, offset) => (offset, endsDoBlock(tokens, index)))

    /**
     * Whether the clause the `ret` at `start` begins runs to the end of its do block. Only the
     * tokens of that block itself are looked at, not those inside brackets nested in it: a
     * `<-`, a `yield`, a `;` before anything but the closing `}`, or a new line that neither
     * continues the line before nor is continued by it, starts another clause. So does a token
     * that starts an operand right after one that finished an operand, on the same line:
     * clauses need no separator.
     */
    private def endsDoBlock(tokens: IndexedSeq[Token], start: Int): Boolean =
      var depth = 0
      var previous = tokens(start)
      var i = start + 1
      var result: Option[Boolean] = None
      while result.isEmpty && i < tokens.length do
        val token = tokens(i)
        val image = token.image
        if depth == 0 then
          if image == "}" || image == ")" || image == "]" then result = Some(true)
          else if image == "<-" || token.kind == K.K_YIELD then result = Some(false)
          else if image == ";" then
            if !tokens.lift(i + 1).exists(_.image == "}") then result = Some(false)
          else if token.beginLine > previous.endLine && i > start + 1 &&
            !Continuations.contains(image) && !OpenEnds.contains(previous.image) then
            result = Some(false)
          else if i > start + 1 && endsOperand(previous) && startsOperand(token) then
            result = Some(false)
        if result.isEmpty then
          image match
            case "(" | "[" | "{" => depth += 1
            case ")" | "]" | "}" => depth -= 1
            case _ => ()
          if depth == 0 then previous = token
          i += 1
      result.getOrElse(true)

    private def isOperandToken(t: Token): Boolean =
      t.kind == K.ID || ValueKeywords.contains(t.image) || (t.kind >= K.INTEGER && t.kind <= K.SCHEME_STRING)

    private def endsOperand(t: Token): Boolean =
      isOperandToken(t) || t.image == ")" || t.image == "]" || t.image == "}"

    private def startsOperand(t: Token): Boolean =
      isOperandToken(t) || ExpressionKeywords.contains(t.image)

    /** The token before a do clause: the block's `{`, a `;`, or the end of the clause before. */
    private def startsClause(previous: Option[Token]): Boolean =
      previous.forall { p =>
        p.image == "{" || p.image == ";" || p.image == ")" || p.image == "]" || p.image == "}" ||
          p.kind == K.ID || ValueKeywords.contains(p.image) ||
          (p.kind >= K.INTEGER && p.kind <= K.SCHEME_STRING)
      }

    /** The keyword was always followed by the clause's expression. */
    private def takesOperand(next: Option[Token]): Boolean =
      next.exists(n => !NotAnExpression.contains(n.image))
