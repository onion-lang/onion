package onion.tools.format

import java.io.StringReader

import onion.compiler.parser.{JJOnionParserConstants as K, JJOnionParserTokenManager, JavaCharStream, Token}

import scala.collection.mutable

/**
 * A source formatter that normalises punctuation spacing and touches nothing else.
 *
 * Onion had no formatter of any kind. This one does considerably less than most, and the
 * boundary is where it is for a measured reason rather than a cautious one. The rule it
 * holds to is that '''whitespace no rule applies to is reproduced byte for byte''' — not
 * approximated, not re-derived from column numbers, copied.
 *
 * '''It does not reindent.''' The obvious design — compute indentation from brace depth —
 * was built and measured against the 171 programs in `run/`, and it rewrote 36% of their
 * lines. Almost all of that was wrong. Onion's continuations are not bracketed: an
 * expression body on the line after `=`, a method chain led by `.filter`, an expression
 * carried on by a trailing `+`, a `from re"…"` or `example { … }` clause under a record.
 * Nothing lexical distinguishes those from a misindented line, so a formatter that
 * computes indentation reindents correct code. Getting indentation right is left to the
 * parser-based formatter this is not.
 *
 * '''It does not move a line break.''' A newline can end a statement in Onion, so
 * re-wrapping is a semantic change, not a cosmetic one.
 *
 * '''It keeps runs of spaces, and tabs.''' Alignment inside a line is often deliberate, and
 * flattening it trades a real intent for a uniformity nobody asked for. What is left is the
 * punctuation that binds to what it touches: `f(a , b)` becomes `f(a, b)`, `f(- 1)` becomes
 * `f(-1)`, `"=" .rep(60)` becomes `"=".rep(60)`.
 *
 * '''No rule touches a bracket''', in either direction, and the corpus is why. Closing `f( a )`
 * up to `f(a)` reads like an improvement until you meet `new Employee( 1, …)` above
 * `new Employee(10, …)`, where the space is a right-aligned ID column. Closing `f (1)` up
 * reads like an improvement until you meet an enum whose cases are padded so their argument
 * lists line up — and there the padding is five spaces on one line and a single space on the
 * next, so not even "collapse a lone space" separates the typo from the intent. Across 182
 * sample programs those two rules fired dozens of times and every single firing was damage —
 * there was no misplaced bracket space in the corpus for them to fix. A rule with no true
 * positives is not a rule.
 *
 * What survives is small on purpose. Run over those same 182 programs the whole formatter
 * changes four lines: three stray spaces before a `.`, and one file missing its final
 * newline. That is the honest size of the problem a lexical formatter can see here.
 *
 * A missing newline at the end of the file is added, which is the one change to layout
 * outside a line it does make. An absent final newline is a defect rather than a style: it
 * is what makes a diff say `\ No newline at end of file`.
 *
 * '''Layout comes from the source text, not from newline tokens.''' Onion's lexer is
 * state-dependent and the parser drives those states, so a token manager run on its own
 * never emits the `EOL` tokens a parse would see — the first version of this produced every
 * file on a single line. Positions were the obvious substitute and are subtly wrong on their
 * own: JavaCC advances a column to the next multiple of eight for a tab, so rebuilding
 * whitespace from columns turned two tab-indented sample programs into eight-space-indented
 * ones — 110 lines of churn in one file, from a formatter that had just declared it would
 * not touch indentation. Whitespace is therefore sliced out of the original text.
 *
 * Comments survive because the lexer hands them over as special tokens attached to the
 * ''following'' token — which means a comment at the end of a file hangs off `EOF`, and an
 * implementation that stops at `EOF` deletes it without a word.
 *
 * Three properties are checked over the whole `run/` corpus rather than on toy inputs:
 * formatting is idempotent, every line break survives, and the token stream — kinds and
 * images, comments included — is unchanged, which for a whitespace-only formatter is what
 * "does not change the program" means. Note that those properties caught neither of the two
 * bugs above: dedenting a whole class body, or expanding every tab, is idempotent and
 * preserves every token. Running it over the corpus and reading the diff is what caught
 * both.
 */
object OnionFormatter:

  /**
   * Never a space before these. Each one is punctuation that binds to what precedes it, so
   * a space there is a slip rather than a choice.
   */
  private val TightBefore = Set(",", ";", "::", ".", "?.", "?[")

  /** Never a space after these, for the same reason in the other direction. */
  private val TightAfter = Set("::", ".", "?.", "@")

  /**
   * After one of these, a `+`/`-`/`!`/`~` is a prefix on what follows rather than an
   * operator between two things. Spacing `f(-1)` into `f(- 1)` parses but reads as a
   * mistake.
   */
  private val PrefixContext = Set(
    "(", "[", "{", ",", ";", "=", "return", "ret", ":", "+", "-", "*", "/", "%",
    "==", "!=", "<", ">", "<=", ">=", "&&", "||", "->", "=>", "<-")

  private val PrefixOperators = Set("+", "-", "!", "~")

  final case class FormatResult(text: String, changed: Boolean)

  def format(source: String): FormatResult =
    val lexemes = lex(source)
    val out = new StringBuilder
    var previous: Option[Lexeme] = None

    lexemes.foreach { lexeme =>
      if lexeme.newlinesBefore > 0 then
        // Blank lines are the author's paragraphing; reproduce them as written.
        out ++= "\n" * lexeme.newlinesBefore
        // The gap is this line's indentation, and goes out untouched.
        out ++= lexeme.gapBefore
      else out ++= gap(previous, lexeme)

      out ++= lexeme.image
      previous = Some(lexeme)
    }

    // The break after the last token is not itself a lexeme, so the file's trailing
    // newlines come from the source -- a blank line at the end is as deliberate as one
    // anywhere else. A file with none gets one, which is the single exception.
    val trailing = math.max(1, source.reverse.takeWhile(_ == '\n').length)
    while out.nonEmpty && out.last == '\n' do out.deleteCharAt(out.length - 1)
    if out.nonEmpty then out ++= "\n" * trailing

    val text = out.toString
    FormatResult(text, text != source)

  // ------------------------------------------------------------------ spacing

  /** The whitespace to put between two lexemes sharing a line. */
  private def gap(previous: Option[Lexeme], next: Lexeme): String =
    previous match
      case None => next.gapBefore
      case Some(before) =>
        val left = before.image
        val right = next.image
        // A comment keeps at least one space, so it never fuses onto the code before it.
        if next.isComment || before.isComment then
          if next.gapBefore.isEmpty then " " else next.gapBefore
        else if TightAfter.contains(left) then ""
        else if TightBefore.contains(right) then ""
        // A bracket gets no rule in either direction. See the note on brackets in this
        // object's documentation: every firing of that rule on real code was damage.
        else if PrefixOperators.contains(left) && before.isPrefix then ""
        // Anything without a rule keeps what the author wrote, byte for byte.
        else next.gapBefore

  // ------------------------------------------------------------------ lexing

  private final case class Lexeme(
    image: String,
    isComment: Boolean,
    isIdentifier: Boolean,
    newlinesBefore: Int,
    /** The whitespace before this token, sliced from the source and emitted verbatim. */
    gapBefore: String,
    isPrefix: Boolean = false
  )

  /**
   * Comments and tokens in source order, each carrying the whitespace that preceded it.
   *
   * The whitespace is cut out of the original text rather than rebuilt from positions,
   * because a column is all a tab leaves behind. Following the source with a cursor is what
   * makes that possible: the gap runs from wherever the last token ended to wherever this
   * one starts, and everything between is whitespace by construction.
   */
  private def lex(source: String): Seq[Lexeme] =
    val raw = mutable.Buffer[Token]()
    val comments = mutable.Set[Token]()
    val manager = new JJOnionParserTokenManager(new JavaCharStream(new StringReader(source)))
    var token = manager.getNextToken()
    var done = false
    while !done do
      val attached = mutable.Buffer[Token]()
      var special = token.specialToken
      while special != null do
        attached.prepend(special)
        special = special.specialToken
      attached.foreach { comment =>
        comments += comment
        raw += comment
      }
      if token.kind == K.EOF then done = true
      else
        raw += token
        token = manager.getNextToken()

    val lines = source.split("\n", -1)
    var line = if raw.isEmpty then 1 else raw.head.beginLine
    var cursor = 0
    var previousEndLine = line

    val lexemes = raw.toSeq.map { t =>
      val newlines = math.max(0, t.beginLine - previousEndLine)
      if newlines > 0 then
        line = t.beginLine
        cursor = 0
      val text = if line >= 1 && line <= lines.length then lines(line - 1) else ""
      val whitespace = text.drop(cursor).takeWhile(c => c == ' ' || c == '\t')

      // A block comment or a multi-line string ends on a later line, so the cursor follows
      // it there rather than counting characters that are no longer on this one.
      val spanned = t.image.count(_ == '\n')
      if spanned > 0 then
        line = t.beginLine + spanned
        cursor = t.image.substring(t.image.lastIndexOf('\n') + 1).length
      else cursor = cursor + whitespace.length + t.image.length
      previousEndLine = t.endLine

      Lexeme(
        image = t.image,
        isComment = comments.contains(t),
        isIdentifier = t.kind == K.ID,
        newlinesBefore = newlines,
        gapBefore = whitespace)
    }
    markPrefixes(lexemes)

  /**
   * Decides, for each `+`/`-`/`!`/`~`, whether it is a prefix operator. A second pass,
   * because it depends on what came before and changes the spacing of what comes after.
   */
  private def markPrefixes(lexemes: Seq[Lexeme]): Seq[Lexeme] =
    var previousCode: Option[String] = None
    lexemes.map { lexeme =>
      if lexeme.isComment then lexeme
      else
        val isPrefix =
          PrefixOperators.contains(lexeme.image) &&
            (previousCode.isEmpty || previousCode.exists(PrefixContext.contains))
        previousCode = Some(lexeme.image)
        lexeme.copy(isPrefix = isPrefix)
    }

  /** Every token and comment image, in order — the invariant a format must not disturb. */
  def signature(source: String): Seq[(Int, String)] =
    val out = mutable.Buffer[(Int, String)]()
    val manager = new JJOnionParserTokenManager(new JavaCharStream(new StringReader(source)))
    var token = manager.getNextToken()
    var done = false
    while !done do
      val specials = mutable.Buffer[Token]()
      var special = token.specialToken
      while special != null do
        specials.prepend(special)
        special = special.specialToken
      specials.foreach(comment => out += ((-1, comment.image)))
      if token.kind == K.EOF then done = true
      else
        out += ((token.kind, token.image))
        token = manager.getNextToken()
    out.toSeq
