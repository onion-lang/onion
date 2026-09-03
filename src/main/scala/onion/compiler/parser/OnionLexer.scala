package onion.compiler.parser

import onion.compiler.parser.{JJOnionParserConstants as K}

/**
 * A handwritten replacement for the JavaCC-generated token manager.
 *
 * It produces the same token stream as `JJOnionParserTokenManager` -- kinds, images,
 * positions, special-token chains, and the two lexical states -- and is a drop-in for it:
 * it extends the generated class so the generated parser, which holds a
 * `JJOnionParserTokenManager`, accepts it unchanged.
 *
 * Why it exists: under the parser most of the source is lexed in `IN_STATEMENT`, and the
 * generated NFA for that state ran about five times slower than the one for `DEFAULT`,
 * which made lexing more than half of the parse time. The two states differ only in
 * whether a newline is skipped or becomes an `EOL` token, so one scanner with a flag does
 * the same work at the speed of a switch on the current character.
 *
 * Fidelity notes, each pinned by the token-golden comparison over `run/` and `src/test/run/`:
 *  - Java unicode escapes are decoded before scanning, with `JavaCharStream`'s
 *    backslash-parity rule and its column bookkeeping (`column += 4` per escape, tab stops
 *    of 8), because the build generates the parser with `JAVA_UNICODE_ESCAPE=true`.
 *  - Maximal munch, ties to the earlier-declared token: `//` beats `/=`, keywords beat `ID`,
 *    `re"`/`file"`/`http"` beat the generic scheme string.
 *  - A scheme string whose prefix is a keyword (`return"x"`) is re-cut into the keyword
 *    followed by a string, exactly as the grammar's one lexical action does.
 *  - A token that fails part-way falls back to its longest accepted prefix, and a character
 *    that starts nothing is an `ERROR` token rather than an exception.
 */
final class OnionLexer(text: String)
  extends JJOnionParserTokenManager(OnionLexer.unusedStream):

  import OnionLexer.*

  // ---------------------------------------------------------------- decoded input

  private val length: Int = text.length
  // A source without a `\u` escape -- nearly all of them -- is scanned in place and its
  // positions are computed by a cursor that follows the tokens (they are emitted in order),
  // instead of two ints per character up front. The escape path keeps the eager tables,
  // because a decoded char's position is not a function of its index alone.
  private val fast: Boolean = text.indexOf("\\u") < 0
  private val chars: Array[Char] = if fast then text.toCharArray else new Array[Char](length)
  private val lines: Array[Int] = if fast then null else new Array[Int](length)
  private val cols: Array[Int] = if fast then null else new Array[Int](length)
  private val n: Int = if fast then length else decode()

  // The position cursor of the fast path: the line/column JavaCharStream would have
  // recorded for chars(cursorIndex), advanced with the same rules as `decode`.
  private var cursorIndex = -1
  private var cursorLine = 1
  private var cursorColumn = 0
  private var cursorPrevCR = false
  private var cursorPrevLF = false

  private def advanceTo(index: Int): Unit =
    if index < cursorIndex then
      cursorIndex = -1; cursorLine = 1; cursorColumn = 0; cursorPrevCR = false; cursorPrevLF = false
    // `cursorPrevLF` doubles as "the next character starts a new line": a line terminator is
    // counted on its own line and the character after it opens the next one. A CR followed by
    // LF is one terminator: the CR defers to the LF (JavaCC's SimpleCharStream does the same).
    var i = cursorIndex
    var line = cursorLine
    var col = cursorColumn
    var newLine = cursorPrevLF
    while i < index do
      i += 1
      val c = chars(i)
      if newLine then
        newLine = false
        col = 1
        line += 1
      else col += 1
      if c == '\n' then newLine = true
      else if c == '\r' then newLine = !(i + 1 < n && chars(i + 1) == '\n')
      else if c == '\t' then
        col -= 1
        col += TabSize - (col % TabSize)
    cursorIndex = i
    cursorLine = line
    cursorColumn = col
    cursorPrevLF = newLine

  private def lineAt(index: Int): Int = if fast then { advanceTo(index); cursorLine } else lines(index)
  private def columnAt(index: Int): Int = if fast then { advanceTo(index); cursorColumn } else cols(index)

  private var pos: Int = 0
  private var pendingSpecial: Token = null

  /**
   * Replays `JavaCharStream.readChar` over the raw text: unicode escapes become one char,
   * every char gets the line/column the stream would have recorded for it.
   */
  private def decode(): Int =
    var line = 1
    var column = 0
    var prevCR = false
    var prevLF = false
    var out = 0
    var i = 0

    // Exactly UpdateLineColumn, minus the buffer bookkeeping.
    def update(c: Char): Unit =
      column += 1
      if prevLF then
        prevLF = false
        column = 1
        line += 1
      else if prevCR then
        prevCR = false
        if c == '\n' then prevLF = true
        else
          column = 1
          line += 1
      c match
        case '\r' => prevCR = true
        case '\n' => prevLF = true
        case '\t' =>
          column -= 1
          column += TabSize - (column % TabSize)
        case _ =>

    def emit(c: Char, l: Int, col: Int): Unit =
      chars(out) = c
      lines(out) = l
      cols(out) = col
      out += 1

    while i < length do
      val c = text.charAt(i)
      if c == '\\' then
        var j = i
        var count = 0
        while j < length && text.charAt(j) == '\\' do
          count += 1
          j += 1
        if j < length && text.charAt(j) == 'u' && (count & 1) == 1 then
          // An odd run of backslashes and a `u`: the escape. Every backslash and the `u`
          // pass through UpdateLineColumn; the decoded char takes the position recorded
          // for the last backslash; the preceding backslashes stay literal.
          var lastLine = 0
          var lastCol = 0
          var k = 0
          while k < count do
            update('\\')
            if k < count - 1 then emit('\\', line, column)
            else
              lastLine = line
              lastCol = column
            k += 1
          update('u')
          j += 1
          while j < length && text.charAt(j) == 'u' do
            column += 1
            j += 1
          if j + 4 > length then
            throw new Error("Invalid escape character at line " + line + " column " + column + ".")
          var value = 0
          var h = 0
          while h < 4 do
            value = (value << 4) | hexval(text.charAt(j + h), line, column)
            h += 1
          column += 4
          emit(value.toChar, lastLine, lastCol)
          i = j + 4
        else
          var k = 0
          while k < count do
            update('\\')
            emit('\\', line, column)
            k += 1
          i = j
      else
        update(c)
        emit(c, line, column)
        i += 1
    out

  private def hexval(c: Char, line: Int, column: Int): Int =
    if c >= '0' && c <= '9' then c - '0'
    else if c >= 'a' && c <= 'f' then c - 'a' + 10
    else if c >= 'A' && c <= 'F' then c - 'A' + 10
    else throw new Error("Invalid escape character at line " + line + " column " + column + ".")

  // ---------------------------------------------------------------- state

  override def SwitchTo(lexState: Int): Unit =
    if lexState >= 2 || lexState < 0 then
      throw new TokenMgrError(
        "Error: Ignoring invalid lexical state : " + lexState + ". State unchanged.",
        TokenMgrError.INVALID_LEXICAL_STATE)
    curLexState = lexState

  // ---------------------------------------------------------------- driver

  override def getNextToken(): Token =
    pendingSpecial = null
    while true do
      if pos >= n then return finish(eofToken())
      val c = chars(pos)
      if c == ' ' || c == '\t' || c == '\f' then pos += 1
      else if c == '\n' || c == '\r' then
        val start = pos
        pos += 1
        if c == '\r' && pos < n && chars(pos) == '\n' then pos += 1
        if curLexState == IN_STATEMENT then return finish(make(K.EOL, start, pos))
      else if c == '/' && pos + 1 < n && chars(pos + 1) == '/' then
        val start = pos
        pos += 2
        while pos < n && chars(pos) != '\n' && chars(pos) != '\r' do pos += 1
        special(make(K.LINE_COMMENT, start, pos))
      else if c == '/' && pos + 1 < n && chars(pos + 1) == '*' then
        val end = blockCommentEnd(pos + 2)
        if end < 0 then return finish(make(K.SLASH, pos, pos + 1).tap(_ => pos += 1))
        special(make(K.MULTI_LINE_COMMENT, pos, end))
        pos = end
      else
        return finish(scanToken())
    null // unreachable

  private def special(t: Token): Unit =
    if pendingSpecial == null then pendingSpecial = t
    else
      t.specialToken = pendingSpecial
      pendingSpecial.next = t
      pendingSpecial = t

  private def finish(t: Token): Token =
    t.specialToken = pendingSpecial
    pendingSpecial = null
    t

  private def eofToken(): Token =
    val t = Token.newToken(K.EOF, "")
    // JavaCC reports the position of the last character it consumed.
    if n > 0 then
      t.beginLine = lineAt(n - 1); t.beginColumn = columnAt(n - 1)
      t.endLine = t.beginLine; t.endColumn = t.beginColumn
    else
      t.beginLine = 0; t.beginColumn = 0; t.endLine = 0; t.endColumn = 0
    t

  /** A token covering `[start, end)` of the decoded input. */
  private def make(kind: Int, start: Int, end: Int): Token =
    // Keywords and operators have one spelling; their image is the shared constant.
    val fixed = fixedImages(kind)
    val image =
      if fixed != null then fixed
      else if kind == K.ID then internedIdentifier(start, end)
      else new String(chars, start, end - start)
    val t = Token.newToken(kind, image)
    t.beginLine = lineAt(start)
    t.beginColumn = columnAt(start)
    t.endLine = lineAt(end - 1)
    t.endColumn = columnAt(end - 1)
    t

  // Identifiers repeat within a file (`i`, `x`, `println`, the same locals over and over);
  // a small open-addressing table hands the same String back instead of a copy per token.
  private val identTable = new Array[String](512)
  private def internedIdentifier(start: Int, end: Int): String =
    val len = end - start
    var h = 0
    var i = start
    while i < end do
      h = h * 31 + chars(i)
      i += 1
    var slot = h & (identTable.length - 1)
    var probes = 0
    while probes < 4 do
      val existing = identTable(slot)
      if existing == null then
        val fresh = new String(chars, start, len)
        identTable(slot) = fresh
        return fresh
      if existing.length == len then
        var j = 0
        while j < len && existing.charAt(j) == chars(start + j) do j += 1
        if j == len then return existing
      slot = (slot + 1) & (identTable.length - 1)
      probes += 1
    new String(chars, start, len)

  private def blockCommentEnd(from: Int): Int =
    var i = from
    while i + 1 < n do
      if chars(i) == '*' && chars(i + 1) == '/' then return i + 2
      i += 1
    -1

  // ---------------------------------------------------------------- tokens

  private def scanToken(): Token =
    val start = pos
    val c = chars(pos)
    if isIdentStart(c) then identifierOrScheme(start)
    else if c >= '0' && c <= '9' then number(start)
    else if c == '.' then
      if pos + 1 < n && chars(pos + 1) >= '0' && chars(pos + 1) <= '9' then number(start)
      else operator(start)
    else if c == '"' then
      if pos + 2 < n && chars(pos + 1) == '"' && chars(pos + 2) == '"' then
        val end = multiLineStringEnd(start + 3)
        if end >= 0 then emit(K.MULTI_LINE_STRING, start, end)
        else
          // `"""` with no close: `""` is an empty STRING, and the third quote starts over.
          emit(K.STRING, start, start + 2)
      else
        val end = stringEnd(start + 1)
        if end >= 0 then emit(K.STRING, start, end) else emit(K.ERROR, start, start + 1)
    else if c == '\'' then
      val end = characterEnd(start + 1)
      if end >= 0 then emit(K.CHARACTER, start, end) else emit(K.ERROR, start, start + 1)
    else if c == '`' then
      var i = start + 1
      while i < n && chars(i) != '`' do i += 1
      if i < n && i > start + 1 then emit(K.QUOTED_ID, start, i + 1)
      else emit(K.BACK_QUOTE, start, start + 1)
    else if c == '#' then
      val end = fqcnEnd(start + 1)
      if end >= 0 then emit(K.FQCN, start, end) else emit(K.SHARP, start, start + 1)
    else if c == '@' then
      var i = start + 1
      while i < n && isIdentPart(chars(i)) do i += 1
      if i > start + 1 then emit(K.ANNOTATION, start, i) else emit(K.ERROR, start, start + 1)
    else operator(start)

  private def emit(kind: Int, start: Int, end: Int): Token =
    pos = end
    make(kind, start, end)

  private def identifierOrScheme(start: Int): Token =
    var i = start + 1
    while i < n && isIdentPart(chars(i)) do i += 1
    val kw = OnionLexer.keywordKind(chars, start, i)
    val word = if kw != -1 then null else new String(chars, start, i - start)
    if i < n && chars(i) == '"' then
      val end = rawStringEnd(i + 1)
      if end >= 0 then
        // A scheme string is the longest match. If its prefix is a keyword the grammar's
        // lexical action gives the string back and emits the keyword alone.
        if kw != -1 then return emit(kw, start, i)
        val kind =
          if word == "re" then K.RE_STRING
          else if word == "file" then K.FILE_STRING
          else if word == "http" then K.HTTP_STRING
          else K.SCHEME_STRING
        return emit(kind, start, end)
    emit(if kw != -1 then kw else K.ID, start, i)

  /** `( ~["\"","\\","\n","\r"] | "\\" ~["\n","\r"] )* "\""` from `from`; the index after the close, or -1. */
  private def rawStringEnd(from: Int): Int =
    var i = from
    while i < n do
      val c = chars(i)
      if c == '"' then return i + 1
      if c == '\n' || c == '\r' then return -1
      if c == '\\' then
        if i + 1 >= n || chars(i + 1) == '\n' || chars(i + 1) == '\r' then return -1
        i += 2
      else i += 1
    -1

  private def multiLineStringEnd(from: Int): Int =
    var i = from
    while i + 2 < n do
      if chars(i) == '"' && chars(i + 1) == '"' && chars(i + 2) == '"' then return i + 3
      i += 1
    -1

  /**
   * The STRING body from `from` (just after the opening quote): the index after the closing
   * quote of the longest accepted match, or -1. The only branch point is `#{`, which may be
   * an interpolation or a literal `#` followed by `{`; the NFA keeps both alive, so both are
   * tried and the longer accepted end wins.
   */
  private def stringEnd(from: Int): Int =
    var i = from
    while i < n do
      val c = chars(i)
      c match
        case '"' => return i + 1
        case '\n' | '\r' => return -1
        case '\\' =>
          val next = escapeEnd(i, allowHash = true)
          if next < 0 then return -1
          i = next
        case '#' =>
          if i + 1 < n && chars(i + 1) == '{' then
            val bodyEnd = interpBodyEnd(i + 2)
            val viaInterp = if bodyEnd >= 0 then stringEnd(bodyEnd) else -1
            val viaPlain = stringEnd(i + 1)
            return math.max(viaInterp, viaPlain)
          i += 1
        case _ => i += 1
    -1

  /** After `#{`: INTERP_BODY then `}`; returns the index after `}` or -1. */
  private def interpBodyEnd(from: Int): Int =
    var i = from
    var depth = 0
    while i < n do
      val c = chars(i)
      c match
        case '}' =>
          if depth == 0 then return i + 1
          depth -= 1
          i += 1
        case '{' =>
          if depth == 1 then return -1
          depth += 1
          i += 1
        case '"' =>
          val end = interpStringEnd(i + 1)
          if end < 0 then return -1
          i = end
        case '\n' | '\r' => return -1
        case _ => i += 1
    -1

  /** INTERP_STRING body: `( ~["\"","\\","\n","\r"] | "\\" ~["\n","\r"] )* "\""`. */
  private def interpStringEnd(from: Int): Int = rawStringEnd(from)

  /**
   * An escape starting at the backslash at `i` (inside CHARACTER or STRING): the index after
   * it, or -1 when it is not one of the accepted forms.
   */
  private def escapeEnd(i: Int, allowHash: Boolean): Int =
    if i + 1 >= n then return -1
    val c = chars(i + 1)
    c match
      case 'n' | 't' | 'b' | 'r' | 'f' | '\\' | '\'' | '"' => i + 2
      case '#' if allowHash => i + 2
      case _ if c >= '0' && c <= '7' =>
        var j = i + 2
        val max = if c <= '3' then 3 else 2
        var count = 1
        while count < max && j < n && chars(j) >= '0' && chars(j) <= '7' do
          j += 1
          count += 1
        j
      case _ => -1

  private def characterEnd(from: Int): Int =
    if from >= n then return -1
    val c = chars(from)
    if c == '\\' then
      // Octal escapes are greedy in the regex, but the whole token still needs the closing
      // quote; try the longest digit run first and fall back to shorter ones.
      val d = if from + 1 < n then chars(from + 1) else '\u0000'
      if d >= '0' && d <= '7' then
        var count = 1
        val max = if d <= '3' then 3 else 2
        while count < max && from + 1 + count < n && chars(from + 1 + count) >= '0' && chars(from + 1 + count) <= '7' do count += 1
        var k = count
        while k >= 1 do
          val close = from + 1 + k
          if close < n && chars(close) == '\'' then return close + 1
          k -= 1
        -1
      else
        val next = escapeEnd(from, allowHash = false)
        if next >= 0 && next < n && chars(next) == '\'' then next + 1 else -1
    else if c == '\'' || c == '\n' || c == '\r' then -1
    else if from + 1 < n && chars(from + 1) == '\'' then from + 2
    else -1

  private def fqcnEnd(from: Int): Int =
    if from >= n || chars(from) != '<' then return -1
    var i = from + 1
    var expectId = true
    while true do
      if expectId then
        if i < n && isIdentStart(chars(i)) then
          i += 1
          while i < n && isIdentPart(chars(i)) do i += 1
          expectId = false
        else return -1
      else if i < n && chars(i) == '.' then
        i += 1
        expectId = true
      else if i < n && chars(i) == '>' then return i + 1
      else return -1
    -1

  // ---------------------------------------------------------------- numbers

  private def number(start: Int): Token =
    val intEnd = integerEnd(start)
    val floatEnd = floatEnd0(start)
    if floatEnd > intEnd then emit(K.FLOAT, start, floatEnd)
    else emit(K.INTEGER, start, intEnd)

  private def isDigit(c: Char): Boolean = c >= '0' && c <= '9'
  private def isHex(c: Char): Boolean = isDigit(c) || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F')

  /** End of the longest INTEGER match at `start`, or `start` if none. */
  private def integerEnd(start: Int): Int =
    val c = chars(start)
    var i = start
    if c >= '1' && c <= '9' then
      i += 1
      while i < n && (isDigit(chars(i)) || chars(i) == '_') do i += 1
    else if c == '0' then
      if start + 2 < n && (chars(start + 1) == 'x' || chars(start + 1) == 'X') && isHex(chars(start + 2)) then
        i = start + 3
        while i < n && (isHex(chars(i)) || chars(i) == '_') do i += 1
      else if start + 2 < n && chars(start + 1) == 'b' && (chars(start + 2) == '0' || chars(start + 2) == '1') then
        i = start + 3
        while i < n && (chars(i) == '0' || chars(i) == '1' || chars(i) == '_') do i += 1
      else
        i = start + 1
        while i < n && ((chars(i) >= '0' && chars(i) <= '7') || chars(i) == '_') do i += 1
    else return start
    if i < n && (chars(i) == 'B' || chars(i) == 'S' || chars(i) == 'L') then i + 1 else i

  /** End of the longest FLOAT match at `start`, or -1 if none. */
  private def floatEnd0(start: Int): Int =
    var best = -1
    var i = start
    if chars(start) == '.' then
      i = digitsEnd(start + 1)
      if i == start + 1 then return -1
      best = withExponentAndSuffix(i, exponentRequired = false, suffixRequired = false)
      return best
    // [0-9][0-9_]*
    i = digitsEnd(start)
    // alt1: "." [0-9][0-9_]* (EXP)? ([FfDd])?
    if i < n && chars(i) == '.' && i + 1 < n && isDigit(chars(i + 1)) then
      val j = digitsEnd(i + 1)
      best = math.max(best, withExponentAndSuffix(j, exponentRequired = false, suffixRequired = false))
    // alt3: EXP ([FfDd])?
    best = math.max(best, withExponentAndSuffix(i, exponentRequired = true, suffixRequired = false))
    // alt4: (EXP)? [FfDd]
    best = math.max(best, withExponentAndSuffix(i, exponentRequired = false, suffixRequired = true))
    best

  /** `[0-9][0-9_]*` from `from` (first char must be a digit); returns the end, or `from` if none. */
  private def digitsEnd(from: Int): Int =
    if from >= n || !isDigit(chars(from)) then return from
    var i = from + 1
    while i < n && (isDigit(chars(i)) || chars(i) == '_') do i += 1
    i

  private def withExponentAndSuffix(from: Int, exponentRequired: Boolean, suffixRequired: Boolean): Int =
    var i = from
    var hasExp = false
    if i < n && (chars(i) == 'e' || chars(i) == 'E') then
      var j = i + 1
      if j < n && (chars(j) == '+' || chars(j) == '-') then j += 1
      if j < n && isDigit(chars(j)) then
        while j < n && isDigit(chars(j)) do j += 1
        i = j
        hasExp = true
    if exponentRequired && !hasExp then return -1
    val hasSuffix = i < n && (chars(i) == 'f' || chars(i) == 'F' || chars(i) == 'd' || chars(i) == 'D')
    if suffixRequired && !hasSuffix then return -1
    if hasSuffix then i + 1 else i

  // ---------------------------------------------------------------- operators

  private def operator(start: Int): Token =
    val c = chars(start)
    val c1 = if start + 1 < n then chars(start + 1) else '\u0000'
    val c2 = if start + 2 < n then chars(start + 2) else '\u0000'
    val c3 = if start + 3 < n then chars(start + 3) else '\u0000'
    inline def tok(kind: Int, len: Int): Token = emit(kind, start, start + len)
    c match
      case '+' => if c1 == '+' then tok(K.PLUSPLUS, 2) else if c1 == '=' then tok(K.ADDEQ, 2) else tok(K.PLUS, 1)
      case '-' => if c1 == '-' then tok(K.MINUSMINUS, 2) else if c1 == '=' then tok(K.SUBEQ, 2) else if c1 == '>' then tok(K.ARROW, 2) else tok(K.MINUS, 1)
      case '*' => if c1 == '=' then tok(K.MULEQ, 2) else tok(K.STAR, 1)
      case '/' => if c1 == '=' then tok(K.DIVEQ, 2) else tok(K.SLASH, 1)
      case '%' => if c1 == '=' then tok(K.MODEQ, 2) else tok(K.PERC, 1)
      case '<' =>
        if c1 == '<' then (if c2 == '=' then tok(K.LSHIFTEQ, 3) else tok(K.L2S, 2))
        else if c1 == '=' then tok(K.LTEQ, 2)
        else if c1 == '-' then tok(K.LARROW, 2)
        else if c1 == ':' then tok(K.SUBTYPE, 2)
        else tok(K.LT, 1)
      case '>' =>
        if c1 == '>' then
          if c2 == '>' then (if c3 == '=' then tok(K.URSHIFTEQ, 4) else tok(K.R3S, 3))
          else if c2 == '=' then tok(K.RSHIFTEQ, 3)
          else tok(K.R2S, 2)
        else if c1 == '=' then tok(K.GTEQ, 2)
        else tok(K.GT, 1)
      case '=' => if c1 == '=' then (if c2 == '=' then tok(K.REFEQ, 3) else tok(K.EQ, 2)) else tok(K.ASSIGN, 1)
      case '!' => if c1 == '=' then (if c2 == '=' then tok(K.REFNOTEQ, 3) else tok(K.NOTEQ, 2)) else if c1 == '!' then tok(K.NOT_NULL, 2) else tok(K.NOT, 1)
      case '&' => if c1 == '&' then tok(K.AND, 2) else if c1 == '=' then tok(K.ANDEQ, 2) else tok(K.AMP, 1)
      case '|' => if c1 == '|' then tok(K.OR, 2) else if c1 == '=' then tok(K.OREQ, 2) else if c1 == '>' then tok(K.PIPELINE, 2) else tok(K.BAR, 1)
      case '^' => if c1 == '=' then tok(K.XOREQ, 2) else tok(K.EOR, 1)
      case '~' => tok(K.BN, 1)
      case ':' => if c1 == ':' then tok(K.COLON2, 2) else tok(K.COLON, 1)
      case ';' => tok(K.SEMI, 1)
      case '.' =>
        if c1 == '.' then (if c2 == '.' then tok(K.ELLIPSIS, 3) else if c2 == '<' then tok(K.DOTDOT_LT, 3) else tok(K.DOTDOT, 2))
        else tok(K.DOT, 1)
      case '{' => tok(K.LBRACE, 1)
      case '}' => tok(K.RBRACE, 1)
      case '(' => tok(K.LPAREN, 1)
      case ')' => tok(K.RPAREN, 1)
      case ',' => tok(K.COMMA, 1)
      case '[' => tok(K.LBRACKET, 1)
      case ']' => tok(K.RBRACKET, 1)
      case '?' => if c1 == ':' then tok(K.ELVIS, 2) else if c1 == '.' then tok(K.SAFE_ACCESS, 2) else if c1 == '[' then tok(K.SAFE_INDEX, 2) else tok(K.QUESTION, 1)
      case _ => tok(K.ERROR, 1)

  extension [A](a: A) private inline def tap(f: A => Unit): A = { f(a); a }

object OnionLexer:
  // The generated token manager wants a character stream, but this lexer never reads it (it
  // scans `text` itself). One shared, never-advanced stream instead of an 8 KiB buffer per
  // lexer -- and a lexer is built per file and per interpolated expression.
  private val unusedStream: JavaCharStream = new JavaCharStream(new java.io.StringReader(""), 1, 1, 1)

  private val TabSize = 8
  private val DEFAULT = 0
  private val IN_STATEMENT = 1

  private def isIdentStart(c: Char): Boolean =
    (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || c == '_'

  private def isIdentPart(c: Char): Boolean =
    isIdentStart(c) || (c >= '0' && c <= '9')

  /** Keyword image -> kind, from the generated constant table; `void` and `Unit` share K_VOID. */
  private val keywords: java.util.HashMap[String, Integer] =
    val m = new java.util.HashMap[String, Integer]()
    var kind = K.K_ABSTRACT
    while kind <= K.K_WHILE do
      val image = K.tokenImage(kind)
      if image.length >= 2 && image.charAt(0) == '"' then m.put(image.substring(1, image.length - 1), kind)
      kind += 1
    m.put("void", K.K_VOID)
    m.put("Unit", K.K_VOID)
    m

  /** The image of each single-spelling token (keywords, operators); null for the rest. */
  private val fixedImages: Array[String] =
    val a = new Array[String](K.tokenImage.length)
    var kind = K.K_ABSTRACT
    while kind <= K.SAFE_INDEX do
      val image = K.tokenImage(kind)
      if image.length >= 2 && image.charAt(0) == '"' then a(kind) = image.substring(1, image.length - 1)
      kind += 1
    a

  /**
   * Keyword lookup without hashing: the keywords are bucketed by length and first character
   * (every bucket holds one to three words), and a candidate is compared character by
   * character against the source. Identifiers are far more common than keywords, and most
   * miss on an empty bucket.
   */
  private val kwWords: Array[String] =
    val words = new java.util.ArrayList[String]()
    keywords.forEach((w, _) => words.add(w))
    words.toArray(new Array[String](0))
  private val kwKinds: Array[Int] = kwWords.map(w => keywords.get(w).intValue)
  private val kwBuckets: Array[Array[Int]] =
    val b = new Array[Array[Int]](32 * 128)
    var i = 0
    while i < kwWords.length do
      val w = kwWords(i)
      val slot = w.length * 128 + w.charAt(0)
      b(slot) = if b(slot) == null then Array(i) else b(slot) :+ i
      i += 1
    b

  private[parser] def keywordKind(chars: Array[Char], start: Int, end: Int): Int =
    val len = end - start
    val c0 = chars(start)
    if len >= 32 || c0 >= 128 then return -1
    val bucket = kwBuckets(len * 128 + c0)
    if bucket == null then return -1
    var bi = 0
    while bi < bucket.length do
      val w = kwWords(bucket(bi))
      var j = 1
      while j < len && chars(start + j) == w.charAt(j) do j += 1
      if j == len then return kwKinds(bucket(bi))
      bi += 1
    -1
