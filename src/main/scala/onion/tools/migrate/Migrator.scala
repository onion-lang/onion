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
 * A rule that cannot tell the old spelling from new code reports the place instead of
 * guessing. A wrong rewrite in someone's source is worse than one they make by hand.
 */
object Migrator:

  /** One rewrite: `length` characters at `offset` become `replacement`. */
  final case class Edit(offset: Int, length: Int, replacement: String, line: Int, column: Int, note: String)

  /** A place a rule could not decide, or one it decided to leave alone, with why. */
  final case class Unsure(line: Int, column: Int, message: String)

  /**
   * A file with an undecided place keeps all of its text: `edits` is then empty, so a file is
   * either fully migrated or left exactly as it was. `notes` are places a rule proved are not
   * the old spelling; they are reported, but need nothing from the user.
   */
  final case class Result(text: String, edits: Seq[Edit], unsure: Seq[Unsure], notes: Seq[Unsure]):
    def changed: Boolean = edits.nonEmpty

  def migrate(source: String): Result =
    val tokens = codeTokens(source)
    val found = RetToYield.find(source, tokens)
    val edits = if found.unsure.nonEmpty then Nil else found.edits
    Result(apply(source, edits), edits, found.unsure, found.notes)

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

  /**
   * `ret e` → `yield e` in a `do[M] { ... }` block (#2291).
   *
   * Before the change `ret` was a keyword, legal only at the start of a do clause. So in a
   * file written before it, every `ret` token is a candidate. A `ret` anywhere else (a
   * declaration, a lambda parameter, a destructuring or pattern binding, an argument) means
   * the file was written since and uses `ret` as an ordinary name. Then a bare `ret` at the
   * start of a clause is ambiguous: such a file's candidates are reported, not rewritten.
   *
   * The old keyword was also legal only in a do block's final clause. A `ret` with another
   * clause after it is a call through a name declared somewhere this file cannot see (an
   * inherited method, another file, a Java class): it is noted and left alone.
   *
   * A final `ret` followed by `(`, `[`, `{`, `-`, `+` or `!` may be that call, an index or an
   * operator on such a name, and rewriting it would silently change what the program
   * computes. It is reported instead. The full rule is on #2442.
   */
  private object RetToYield:
    final case class Found(edits: Seq[Edit], unsure: Seq[Unsure], notes: Seq[Unsure])

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

    /** After `ret`, these could continue a name `ret` as well as start the old keyword's operand. */
    private val NameContinuations = Set("(", "[", "{", "-", "+", "!")
    /** Keywords that start an expression, so after a finished operand they start a new clause. */
    private val ExpressionKeywords = Set("new", "if", "select", "do", "try")

    private enum Bracket:
      case Paren, Square, Brace, DoBrace

    def find(source: String, tokens: IndexedSeq[Token]): Found =
      val candidates = mutable.Buffer[(Token, Int)]()
      var usesRetAsName = false
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
            else usesRetAsName = true
          case _ => ()
        if token.image == "{" then nextBraceIsDo = false
      }

      val located = candidates.toSeq.flatMap { (token, index) =>
        offsetOf(source, token.beginLine, token.beginColumn)
          .filter(offset => source.startsWith("ret", offset))
          .map(offset => (token, index, offset))
      }
      val (last, notLast) = located.partition((_, index, _) => endsDoBlock(tokens, index))
      val notes = notLast.map { (token, _, _) =>
        Unsure(token.beginLine, token.beginColumn,
          "this `ret` is not the last clause of its do block, so it is a call through a name " +
            "`ret` declared elsewhere, not the old keyword; left as written")
      }
      val (ambiguous, clear) =
        if usesRetAsName then (last, Nil)
        else last.partition((_, index, _) => tokens.lift(index + 1).exists(t => NameContinuations.contains(t.image)))
      val unsure = ambiguous.map { (token, _, _) =>
        val why =
          if usesRetAsName then "the file also uses `ret` as a name"
          else "it could also be a call or an operator on a name `ret` declared elsewhere"
        Unsure(token.beginLine, token.beginColumn,
          s"this `ret` may be the old do-block keyword, but $why; " +
            "if it is the keyword, change it to `yield` by hand, otherwise leave it")
      }
      val edits = clear.map { (token, _, offset) =>
        Edit(offset, 3, "yield", token.beginLine, token.beginColumn, "ret -> yield")
      }
      Found(edits, unsure, notes)

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
