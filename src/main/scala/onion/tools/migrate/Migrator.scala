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

  /**
   * One rewrite: `length` characters at `offset` become `replacement`. `subject` names the old
   * spelling for messages (`` `ret` ``, `` `law` ``, `` `example` ``). A `clause` edit replaces
   * whole tokens with a different number of tokens, so it is checked by re-lexing the pieces
   * (see [[MigrateCommand]]), not token by token.
   */
  final case class Edit(
    offset: Int,
    length: Int,
    replacement: String,
    line: Int,
    column: Int,
    note: String,
    subject: String = "`ret`",
    clause: Boolean = false,
    oldSpelling: Option[String] = None,
    rewrittenAs: String = "rewritten as a contract predicate",
    places: Seq[(Int, Int)] = Seq.empty
  ):
    /** Every position a compile error may name for this edit: its own, and those of the edits folded into it. */
    def allPlaces: Seq[(Int, Int)] = (line, column) +: places

  /** A place a rule left alone, or could not change, with why. */
  final case class Note(line: Int, column: Int, message: String)

  /**
   * `text` is the source with every edit applied. `notes` are places a rule proved are not
   * the old spelling; they are reported, but need nothing from the user.
   */
  final case class Result(text: String, edits: Seq[Edit], notes: Seq[Note]):
    def changed: Boolean = edits.nonEmpty

  /**
   * `declared` holds the type names other files named alongside declare: a rewrite never
   * writes a bare name one of them could capture.
   */
  def migrate(source: String, declared: Set[String] = Set.empty): Result =
    val (clauses, clauseNotes) = LawExampleToPredicate.find(source)
    val insideClause = (offset: Int) => clauses.exists(c => offset >= c.offset && offset < c.offset + c.length)
    val (last, notLast) = RetToYield.find(source).filterNot((offset, _) => insideClause(offset)).partition(_._2)
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
    val selfEdits = SelfToThis.find(source).filterNot(insideClause).map { offset =>
      val (line, column) = lineColumnOf(source, offset)
      Edit(offset, 4, "this", line, column, "self -> this", subject = "`self`")
    }
    // `self` -> `this` keeps every offset, so the randomInt rule reads the text with it applied:
    // a `self` in a call's arguments is rewritten inside that call's one edit, and its
    // position, where the compiler reports E0117, is one of that edit's places.
    val (randomInts, randomIntNotes) = RandomIntToNextInt.find(apply(source, selfEdits), declared)
    val within = (offset: Int, e: Edit) => offset >= e.offset && offset < e.offset + e.length
    val calls = randomInts.filterNot(e => insideClause(e.offset)).map { call =>
      val folded = selfEdits.filter(s => within(s.offset, call))
      call.copy(places = call.places ++ folded.map(s => (s.line, s.column)))
    }
    val selves = selfEdits.filterNot(s => calls.exists(call => within(s.offset, call)))
    val all = (edits ++ selves ++ clauses ++ calls).sortBy(_.offset)
    Result(apply(source, all), all, (notes ++ clauseNotes ++ randomIntNotes).sortBy(n => (n.line, n.column)))

  /**
   * Where the character at `line`:`column` of `result.text` was in `source`, and the edit whose
   * replacement holds it, if one does: a compile of the rewritten text reports positions in
   * that text, which was never written to disk (#2765). A position inside an edit maps to the
   * edit's own start. `None` when no character sits at that position.
   */
  def origin(source: String, result: Result, line: Int, column: Int): Option[(Int, Int, Option[Edit])] =
    offsetOf(result.text, line, column).map { at =>
      var delta = 0
      var found: Option[Either[Int, Edit]] = None
      result.edits.sortBy(_.offset).foreach { e =>
        if found.isEmpty then
          val from = e.offset + delta
          if at < from then found = Some(Left(at - delta))
          else if at < from + e.replacement.length then found = Some(Right(e))
          else delta += e.replacement.length - e.length
      }
      found.getOrElse(Left(at - delta)) match
        case Left(offset) =>
          val (l, c) = lineColumnOf(source, offset)
          (l, c, None)
        case Right(e) => (e.line, e.column, Some(e))
    }

  /** `source` with only the clause edits of `result` applied: what the `ret` edits are checked against. */
  def withClausesOnly(source: String, result: Result): String =
    apply(source, result.edits.filter(_.clause))

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

  /** The names `source` declares a type under (`class`, `interface`, `record`, `enum`). */
  def declaredTypes(source: String): Set[String] =
    val tokens =
      try codeTokens(source)
      catch case _: Exception => IndexedSeq.empty
    tokens.indices.collect {
      case i if i + 1 < tokens.length && Set("class", "interface", "record", "enum")(tokens(i).image) &&
          tokens(i + 1).kind == K.ID => tokens(i + 1).image
    }.toSet

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
   * `OnionMath::randomInt(min, max)` → `Rand::nextInt(min, max + 1)` (#3069).
   *
   * `randomInt` included `max`; `Rand::nextInt` excludes it, so the rewrite adds one to the
   * second argument. A decimal literal is incremented (`6` → `7`), an argument ending in
   * `- 1` loses it (`n - 1` → `n`), an argument built only from names, literals, calls and
   * `+ - * / %` gets `+ 1`, and anything else is parenthesized first (`(a ?: b) + 1`).
   * `onion.OnionMath::randomInt` is matched too. A `max` of `2147483647` has no `+ 1`, so it
   * is noted and left as written. As for `ret`, [[MigrateCommand]] keeps a rewrite only when
   * the file does not compile as written and does compile rewritten, so a user's own class
   * named `OnionMath` is never touched.
   */
  private object RandomIntToNextInt:
    private val Subject = "`OnionMath::randomInt`"

    /** Tokens that bind at least as tightly as a binary `+`, so `arg + 1` keeps its meaning. */
    private val Tight = Set(".", "::", "?.", "+", "-", "*", "/", "%")

    /**
     * Every call in `source`, including those in the code of a string's `#{...}`, which the
     * lexer keeps inside one string token. A call there is a plain edit inside that token, so
     * [[MigrateCommand]] checks it as a rewrite within the token, not as whole tokens.
     */
    def find(source: String, declared: Set[String] = Set.empty): (Seq[Edit], Seq[Note]) =
      val tokens =
        try codeTokens(source)
        catch case _: Exception => IndexedSeq.empty
      find(source, tokens, Spelling(source, tokens, declared))

    private def find(source: String, tokens: IndexedSeq[Token], spelling: Spelling): (Seq[Edit], Seq[Note]) =
      val (edits, notes) = calls(source, tokens, 0, tokens.length, spelling)
      val interpolated = tokens.flatMap { token =>
        val bodies = interpolationBodies(token)
        if bodies.isEmpty then Seq.empty
        else
          offsetOf(source, token.beginLine, token.beginColumn)
            .filter(offset => source.startsWith(token.image, offset))
            .toSeq
            .flatMap { offset =>
              bodies.flatMap { (start, body) =>
                val bodyTokens =
                  try codeTokens(body)
                  catch case _: Exception => IndexedSeq.empty
                val (inner, innerNotes) = find(body, bodyTokens, spelling)
                val base = offset + start
                val at = (line: Int, column: Int) =>
                  offsetOf(body, line, column).map(o => lineColumnOf(source, base + o)).getOrElse((token.beginLine, token.beginColumn))
                inner.map { e =>
                  val (line, column) = at(e.line, e.column)
                  Left(e.copy(offset = base + e.offset, line = line, column = column, clause = false,
                    places = e.places.map(at.tupled)))
                } ++ innerNotes.map { n =>
                  val (line, column) = at(n.line, n.column)
                  Right(n.copy(line = line, column = column))
                }
              }
            }
      }
      (edits ++ interpolated.collect { case Left(e) => e }, notes ++ interpolated.collect { case Right(n) => n })

    /**
     * How a file spells the old call and the new one.
     *  - `heads`: the qualifiers that name `onion.OnionMath` here: `OnionMath`, and any alias an
     *    import gives it (`import { onion.OnionMath as OM; }`).
     *  - `bare`: a bare `randomInt(...)` may be the old method through a static import
     *    (`import { onion.OnionMath::* }`), unless the file declares a `randomInt` of its own.
     *  - `rand`: `Rand`, or `onion.Rand` when this file, or another file named alongside it,
     *    gives the name `Rand` a meaning of its own (a class, an import, a variable).
     */
    private final case class Spelling(heads: Set[String], bare: Boolean, rand: String)

    private object Spelling:
      def apply(source: String, tokens: IndexedSeq[Token], declared: Set[String]): Spelling =
        val image = (i: Int) => if i >= 0 && i < tokens.length then tokens(i).image else ""
        val aliases = tokens.indices.collect {
          case i if image(i) == "onion" && image(i + 1) == "." && image(i + 2) == "OnionMath" && image(i + 3) == "as" &&
              i + 4 < tokens.length && tokens(i + 4).kind == K.ID => image(i + 4)
        }.toSet
        val ownRandomInt = tokens.indices.exists(i => image(i) == "randomInt" && image(i - 1) == "def")
        val ownRand = declared.contains("Rand") || tokens.indices.exists { i =>
          image(i) == "Rand" && image(i + 1) != "::" && !(image(i - 1) == "." && image(i - 2) == "onion")
        }
        Spelling(Set("OnionMath") ++ aliases, !ownRandomInt, if ownRand then "onion.Rand" else "Rand")

    /** The index of the first token of an old call whose `(` is at `open`, if one ends there. */
    private def head(tokens: IndexedSeq[Token], open: Int, spelling: Spelling): Option[Int] =
      val image = (i: Int) => if i >= 0 && i < tokens.length then tokens(i).image else ""
      val member = Set(".", "::", "?.")
      if image(open) != "(" || image(open - 1) != "randomInt" then None
      else if image(open - 2) == "::" then
        val q = open - 3
        if image(q) == "OnionMath" && image(q - 1) == "." && image(q - 2) == "onion" && !member(image(q - 3)) then Some(q - 2)
        else if spelling.heads(image(q)) && q >= 0 && tokens(q).kind == K.ID && !member(image(q - 1)) then Some(q)
        else None
      else if spelling.bare && !member(image(open - 2)) && image(open - 2) != "def" then Some(open - 1)
      else None

    /**
     * The calls whose tokens lie in `[from, until)`. A call in another call's arguments is
     * rewritten inside that call's one edit, so edits never overlap; its position is kept as
     * one of that edit's places, since the compiler reports the inner call first.
     */
    private def calls(source: String, tokens: IndexedSeq[Token], from: Int, until: Int, spelling: Spelling): (Seq[Edit], Seq[Note]) =
      val edits = mutable.Buffer[Edit]()
      val notes = mutable.Buffer[Note]()
      var i = from
      while i < until do
        head(tokens, i, spelling) match
          case Some(first) if first >= from =>
            arguments(tokens, i).filter(_._2 < until) match
              case None => i += 1
              case Some((comma, close)) =>
                val (inner, innerNotes) = calls(source, tokens, i + 1, close, spelling)
                notes ++= innerNotes
                rewrite(source, tokens, first, i, comma, close, inner, spelling) match
                  case Right(e) => edits += e
                  case Left(n) =>
                    notes ++= n
                    edits ++= inner
                i = close + 1
          case _ => i += 1
      (edits.toSeq, notes.toSeq)

    private def rewrite(
      source: String,
      tokens: IndexedSeq[Token],
      first: Int,
      open: Int,
      comma: Int,
      close: Int,
      inner: Seq[Edit],
      spelling: Spelling
    ): Either[Option[Note], Edit] =
      val result = for
        from <- start(source, tokens(first))
        paren <- start(source, tokens(open))
        maxFrom <- start(source, tokens(comma + 1))
        last = tokens(close - 1)
        maxTo <- start(source, last).map(_ + last.image.length)
        closeAt <- start(source, tokens(close))
      yield (from, paren, maxFrom, maxTo, closeAt)
      result match
        case None => Left(None)
        case Some((from, paren, maxFrom, maxTo, closeAt)) =>
          val max = tokens.slice(comma + 1, close)
          // The compiler reports the missing method at `randomInt`, so the edit is placed there.
          val name = tokens(open - 1)
          val (line, column) = (name.beginLine, name.beginColumn)
          // `source` from `a` to `b`, with the calls inside it already rewritten.
          val rewritten = (a: Int, b: Int) =>
            apply(source.substring(a, b), inner.filter(e => e.offset >= a && e.offset < b).map(e => e.copy(offset = e.offset - a)))
          plusOne(rewritten(maxFrom, maxTo), max) match
            case None =>
              Left(Some(Note(line, column,
                s"this $Subject has a max of 2147483647, which Rand::nextInt cannot include (its max is exclusive); " +
                  "left as written. Write Rand::nextInt() for any Int, or pick the bound by hand")))
            case Some(newMax) =>
              val replacement = spelling.rand + "::nextInt" + rewritten(paren, maxFrom) + newMax + rewritten(maxTo, closeAt + 1)
              Right(Edit(from, closeAt + 1 - from, replacement, line, column,
                "OnionMath::randomInt(min, max) -> Rand::nextInt(min, max + 1)",
                subject = Subject, clause = true, oldSpelling = Some(s"the removed $Subject"),
                rewrittenAs = "rewritten as Rand::nextInt",
                places = inner.flatMap(_.allPlaces)))

    /** `Some(text)` for the new max, or `None` when it cannot be one more (`2147483647`). */
    private def plusOne(text: String, max: IndexedSeq[Token]): Option[String] =
      val digits = text.replace("_", "")
      if max.length == 1 && max.head.kind == K.INTEGER && digits.forall(_.isDigit) then
        val value = BigInt(digits)
        if value >= Int.MaxValue then None else Some((value + 1).toString)
      else
        val simple = topLevel(max).forall(t => t.kind == K.ID || t.kind == K.INTEGER || Tight.contains(t.image) ||
          t.kind == K.K_THIS || t.kind == K.K_SELF || t.image == "(" || t.image == "[")
        val beforeMinus = if max.length >= 3 then Some(max(max.length - 3)) else None
        val operand = beforeMinus.exists(t => t.kind == K.ID || t.kind == K.INTEGER || t.image == ")" || t.image == "]")
        if simple && operand && max(max.length - 2).image == "-" && max.last.image == "1" then
          Some(text.substring(0, text.lastIndexOf('-')).stripTrailing)
        else if simple then Some(text + " + 1")
        else Some("(" + text + ") + 1")

    /** The tokens of `max` outside any bracket; a bracket's opener stands for its group. */
    private def topLevel(max: IndexedSeq[Token]): Seq[Token] =
      var depth = 0
      max.filter { t =>
        val atTop = depth == 0
        t.image match
          case "(" | "[" | "{" => depth += 1
          case ")" | "]" | "}" => depth -= 1
          case _ => ()
        atTop && !Set(")", "]", "}").contains(t.image)
      }

    /** For the `(` at `open`: the index of the one top-level comma and of the closing `)`. */
    private def arguments(tokens: IndexedSeq[Token], open: Int): Option[(Int, Int)] =
      var depth = 0
      var i = open
      var commas = List.empty[Int]
      var close: Option[Int] = None
      while close.isEmpty && i < tokens.length do
        tokens(i).image match
          case "(" | "[" | "{" => depth += 1
          case ")" | "]" | "}" =>
            depth -= 1
            if depth == 0 then close = Some(i)
          case "," if depth == 1 => commas ::= i
          case _ => ()
        i += 1
      close.flatMap { c =>
        commas match
          case comma :: Nil if comma > open + 1 && c > comma + 1 => Some((comma, c))
          case _ => None
      }

    private def start(source: String, token: Token): Option[Int] =
      offsetOf(source, token.beginLine, token.beginColumn).filter(source.startsWith(token.image, _))

  /**
   * `self` → `this` (RFC #2788, #2737). `self` is a reserved word that always meant the current
   * instance, so every `self` code token is the old spelling, and so is every `self` in the
   * code of a string's `#{...}`. Plain string text, raw and scheme strings, `\#{` escapes,
   * comments and a backticked `` `self` `` are other tokens, and are left alone.
   */
  private object SelfToThis:
    def find(source: String): Seq[Int] =
      val tokens =
        try codeTokens(source)
        catch case _: Exception => IndexedSeq.empty
      tokens.flatMap { token =>
        offsetOf(source, token.beginLine, token.beginColumn)
          .filter(offset => source.startsWith(token.image, offset))
          .toSeq
          .flatMap { offset =>
            if token.kind == K.K_SELF then Seq(offset)
            else interpolationBodies(token).flatMap((start, body) => find(body).map(offset + start + _))
          }
      }

  /**
   * `law name(p: T) { e }` and `example [name] { e }` → a contract predicate (#2740).
   *
   * #2609 removed both clauses. A record's clause becomes
   * `public: static def name(p: T): Boolean ensures result = e`, and a top-level `example`
   * becomes `def name(): Boolean ensures result = e`, the forms the migration guide shows.
   * A body of one expression becomes `= e`; a body of several statements stays a block, with
   * `return` put in front of its last statement, as the old clause returned it. An `example`
   * with no name is named `example1`, `example2`, ... in order, per record and per file.
   *
   * Only the two places the old grammar accepted are looked at: a record's body, and the top
   * level of a file. A top-level `example { e }` also reads today as a call of a function
   * `example` with a trailing lambda, so, as for `ret`, [[MigrateCommand]] keeps a rewrite only
   * when the file does not compile as written and does compile rewritten.
   */
  private object LawExampleToPredicate:
    private enum Bracket:
      case Paren, Square, Brace, RecordBody

    private val SectionLabels = Set("public", "protected", "private")

    /** Before an `example` that names a value, so a top-level `example { e }` may be its call. */
    private val Declarers = Set("def", "val", "var", "::", "as")

    /** After these, a header's next line still continues it. */
    private val HeaderWords = Set("from", "derive", "conforms", "{")

    def find(source: String): (Seq[Edit], Seq[Note]) =
      val tokens =
        try codeTokens(source)
        catch case _: Exception => IndexedSeq.empty
      val edits = mutable.Buffer[Edit]()
      val stack = mutable.Stack[Bracket]()
      // The stack depth a `record` header was seen at, until its body's `{` or its end.
      var recordHeader: Option[Int] = None
      // How many unnamed examples each record body (by its `{` index) and the file have had.
      val unnamed = mutable.Map[Int, Int]().withDefaultValue(0)
      val bodies = mutable.Stack[Int]()
      val notes = mutable.Buffer[Note]()
      // The access section in force in each record body (by its `{` index); members default to private.
      val sections = mutable.Map[Int, String]().withDefaultValue("private")
      // Each record clause's edit index, the section it sat in, and its closing `}`'s index.
      val inRecordClauses = mutable.Buffer[(Int, String, Int)]()
      // A file that declares or imports a name `example` may call it as `example { e }` at the
      // top level, so no top-level `example` there is taken for the old clause (#2765).
      val exampleIsName = tokens.indices.exists { j =>
        j > 0 && tokens(j).kind == K.ID && tokens(j).image == "example" && Declarers.contains(tokens(j - 1).image)
      }
      // A wildcard static import (`import { lib.Lib::* }`) may bring in an `example` no token
      // here names, so a top-level `example` there is left alone too (#2765).
      val wildcardImport = !exampleIsName && tokens.indices.exists { j =>
        j > 0 && tokens(j).image == "*" && tokens(j - 1).image == "::"
      }
      var i = 0
      while i < tokens.length do
        val token = tokens(i)
        val image = token.image
        val previous = if i > 0 then Some(tokens(i - 1)) else None
        val atTop = stack.isEmpty
        val inRecord = stack.headOption.contains(Bracket.RecordBody)
        val clause =
          if token.kind != K.ID || !(atTop || inRecord) then None
          else if image == "law" && inRecord then lawAt(tokens, i)
          else if image == "example" && (inRecord || startsStatement(previous, token)) then
            exampleAt(tokens, i).filter { _ =>
              val call = !inRecord && (exampleIsName || wildcardImport)
              if call then
                val (line, column) = start(source, token).map(lineColumnOf(source, _)).getOrElse((token.beginLine, token.beginColumn))
                notes += Note(line, column,
                  if exampleIsName then
                    "this `example` is a name, not the old `example` clause: the file declares or imports " +
                      "`example`, so `example { ... }` at the top level is its call; left as written"
                  else
                    "this `example` may be a call: the file has a `::*` import, which may bring in a function " +
                      "`example`; left as written. If it is the old `example` clause, " +
                      "write `def name(): Boolean ensures result = ...` instead")
              !call
            }
          else None
        clause match
          case Some((nameIndex, params, open, close)) =>
            val scope = if inRecord then bodies.head else -1
            val name = nameIndex.map(tokens(_).image).getOrElse {
              unnamed(scope) += 1
              s"example${unnamed(scope)}"
            }
            // A clause right after another rewritten one is already under its `public:`.
            val labelled = inRecord && edits.lastOption.exists { e =>
              e.clause &&
                start(source, token).exists(at => at >= e.offset + e.length && source.substring(e.offset + e.length, at).isBlank)
            }
            render(source, tokens, i, name, params, open, close, inRecord, labelled).foreach { e =>
              if inRecord then inRecordClauses += ((edits.length, sections(scope), close))
              edits += e
            }
            i = close + 1
          case None =>
            recordHeader.foreach { depth =>
              if stack.size == depth && image != "{" && endsHeader(token, previous) then recordHeader = None
            }
            image match
              case "(" => stack.push(Bracket.Paren)
              case "[" => stack.push(Bracket.Square)
              case "{" =>
                if recordHeader.contains(stack.size) then
                  recordHeader = None
                  bodies.push(i)
                  stack.push(Bracket.RecordBody)
                else stack.push(Bracket.Brace)
              case ")" | "]" | "}" =>
                if stack.nonEmpty then
                  if stack.pop() == Bracket.RecordBody && bodies.nonEmpty then bodies.pop()
                recordHeader.foreach(depth => if stack.size < depth then recordHeader = None)
              case _ => ()
            if inRecord && SectionLabels.contains(image) && tokens.lift(i + 1).exists(_.image == ":") then
              sections(bodies.head) = image
            if token.kind == K.K_RECORD then recordHeader = Some(stack.size)
            i += 1
      restoreSections(source, tokens, edits, inRecordClauses.toSeq)
      (edits.toSeq, notes.toSeq)

    /**
     * A record clause's predicate goes under an inserted `public:`. Unless what follows it is
     * another clause, a section label or the body's end, the section that was in force is
     * labelled again after it, so the members after the clause keep their access (#2765).
     */
    private def restoreSections(
      source: String,
      tokens: IndexedSeq[Token],
      edits: mutable.Buffer[Edit],
      clauses: Seq[(Int, String, Int)]
    ): Unit =
      clauses.foreach { (index, section, close) =>
        val next = tokens.lift(close + 1)
        val nextIsClause = next.flatMap(start(source, _)).exists(at => edits.exists(e => e.clause && e.offset == at))
        val nextIsLabel = next.exists(t => SectionLabels.contains(t.image)) && tokens.lift(close + 2).exists(_.image == ":")
        if section != "public" && !nextIsClause && !nextIsLabel && !next.exists(_.image == "}") then
          val e = edits(index)
          val lineStart = source.lastIndexOf('\n', e.offset - 1) + 1
          val before = source.substring(lineStart, e.offset)
          val ownLine = before.forall(c => c == ' ' || c == '\t')
          val restored = if ownLine then s"\n$before$section:" else s" $section:"
          edits(index) = e.copy(replacement = e.replacement + restored)
      }

    /** A token at the header's own depth that cannot be part of `record N[T](...) from .. derive!(..) conforms T, U`. */
    private def endsHeader(token: Token, previous: Option[Token]): Boolean =
      val image = token.image
      val fits = token.kind == K.ID || token.kind == K.K_RECORD || token.kind == K.RE_STRING ||
        Set("(", "[", ")", "]", ".", ",", "?", "!").contains(image)
      val newLine = previous.exists(p => token.beginLine > p.endLine)
      !fits || (newLine && !HeaderWords.contains(image) && !previous.exists(p => p.image == "," || p.image == "conforms"))

    private def startsStatement(previous: Option[Token], token: Token): Boolean =
      previous.forall(p => p.image == ";" || p.image == "}" || token.beginLine > p.endLine)

    /** `law name ( params ) { body }`: (name, params' bounds, body's `{`, body's `}`). */
    private def lawAt(tokens: IndexedSeq[Token], i: Int): Option[(Option[Int], Option[(Int, Int)], Int, Int)] =
      if !tokens.lift(i + 1).exists(_.kind == K.ID) || !tokens.lift(i + 2).exists(_.image == "(") then None
      else matching(tokens, i + 2).flatMap { closeParen =>
        if !tokens.lift(closeParen + 1).exists(_.image == "{") then None
        else matching(tokens, closeParen + 1).map(close => (Some(i + 1), Some((i + 2, closeParen)), closeParen + 1, close))
      }

    /** `example [name] { body }`. */
    private def exampleAt(tokens: IndexedSeq[Token], i: Int): Option[(Option[Int], Option[(Int, Int)], Int, Int)] =
      val named = tokens.lift(i + 1).exists(_.kind == K.ID) && tokens.lift(i + 2).exists(_.image == "{")
      val open = if named then i + 2 else i + 1
      if !tokens.lift(open).exists(_.image == "{") then None
      else matching(tokens, open).map(close => (if named then Some(i + 1) else None, None, open, close))

    /** The index of the bracket that closes the one at `open`. */
    private def matching(tokens: IndexedSeq[Token], open: Int): Option[Int] =
      var depth = 0
      var i = open
      var found: Option[Int] = None
      while found.isEmpty && i < tokens.length do
        tokens(i).image match
          case "(" | "[" | "{" => depth += 1
          case ")" | "]" | "}" =>
            depth -= 1
            if depth == 0 then found = Some(i)
          case _ => ()
        i += 1
      found

    private def start(source: String, token: Token): Option[Int] =
      offsetOf(source, token.beginLine, token.beginColumn).filter(source.startsWith(token.image, _))

    private def render(
      source: String,
      tokens: IndexedSeq[Token],
      at: Int,
      name: String,
      params: Option[(Int, Int)],
      open: Int,
      close: Int,
      inRecord: Boolean,
      labelled: Boolean
    ): Option[Edit] =
      for
        from <- start(source, tokens(at))
        openAt <- start(source, tokens(open))
        closeAt <- start(source, tokens(close))
        paramText <- params match
          case Some((p, q)) =>
            for a <- start(source, tokens(p)); b <- start(source, tokens(q)) yield source.substring(a + 1, b)
          case None => Some("")
        body <- bodyText(source, tokens, open, close, openAt, closeAt)
      yield
        val lineStart = source.lastIndexOf('\n', from - 1) + 1
        val before = source.substring(lineStart, from)
        val ownLine = before.forall(c => c == ' ' || c == '\t')
        val signature = (if inRecord then "static def " else "def ") + s"$name($paramText): Boolean"
        val label = inRecord && !labelled
        val text =
          if ownLine then
            // A record's predicate sits one step in from its `public:`.
            val extra = if inRecord then "  " else ""
            val member = before + extra
            (if label then s"public:\n$member" else extra) +
              s"$signature\n$member  ensures result\n$member  " + body
          else (if label then "public: " else "") + s"$signature ensures result " + body
        val (line, column) = lineColumnOf(source, from)
        val subject = tokens(at).image
        Edit(from, closeAt + 1 - from, text, line, column, s"$subject -> contract predicate `$name`",
          s"`$subject`", clause = true)

    /**
     * `= e` for a body of one expression, else the block as written with `return` in front of
     * its last statement. `None` for an empty body, which never held.
     */
    private def bodyText(
      source: String,
      tokens: IndexedSeq[Token],
      open: Int,
      close: Int,
      openAt: Int,
      closeAt: Int
    ): Option[String] =
      if close == open + 1 then None
      else
        val starts = statementStarts(tokens, open, close)
        val first = tokens(open + 1)
        val last = tokens(close - 1)
        for
          firstAt <- start(source, first)
          lastAt <- start(source, last)
          lastStart <- start(source, tokens(starts.last))
        yield
          val lastEnd = lastAt + last.image.length
          val bare = source.substring(openAt + 1, firstAt).isBlank && source.substring(lastEnd, closeAt).isBlank
          val head = tokens(starts.last)
          if starts.size == 1 && bare && head.kind != K.K_VAL && head.kind != K.K_VAR then
            if head.kind == K.K_RETURN then
              val next = tokens.lift(starts.last + 1).filter(_ => starts.last + 1 < close)
              next.flatMap(start(source, _)).fold("= " + source.substring(firstAt, lastEnd))(n => "= " + source.substring(n, lastEnd))
            else "= " + source.substring(firstAt, lastEnd)
          else if head.kind == K.K_RETURN then source.substring(openAt, closeAt + 1)
          else source.substring(openAt, lastStart) + "return " + source.substring(lastStart, closeAt + 1)

    /** The first token of each statement directly inside the block from `open` to `close`. */
    private def statementStarts(tokens: IndexedSeq[Token], open: Int, close: Int): Seq[Int] =
      val starts = mutable.Buffer(open + 1)
      var depth = 0
      var previous = tokens(open + 1)
      var i = open + 1
      while i < close do
        val token = tokens(i)
        if depth == 0 && i > open + 1 then
          val separated = previous.image == ";" ||
            (token.beginLine > previous.endLine && !RetToYield.continues(previous, token))
          if separated && token.image != ";" then starts += i
        token.image match
          case "(" | "[" | "{" => depth += 1
          case ")" | "]" | "}" => depth -= 1
          case _ => ()
        if depth == 0 then previous = token
        i += 1
      starts.toSeq

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

    /** Whether `token`, on a new line after `previous`, continues the same expression. */
    def continues(previous: Token, token: Token): Boolean =
      Continuations.contains(token.image) || OpenEnds.contains(previous.image)

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
