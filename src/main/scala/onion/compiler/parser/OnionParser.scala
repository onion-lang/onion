package onion.compiler.parser

import onion.compiler.{AST, Location}
import onion.compiler.parser.{JJOnionParserConstants as K}

import scala.collection.mutable.ArrayBuffer

/**
 * A handwritten recursive-descent parser for Onion, producing the same AST as the
 * JavaCC-generated `JJOnionParser` for every program that parser accepts.
 *
 * It exists for speed: JavaCC's generated code decides most choices by syntactic scans
 * that re-walk the token stream, and parsing was a sixth of a warm compilation. This
 * parser decides by looking at token kinds, and backtracks over its own token buffer only
 * where the grammar genuinely needs a lookahead of more than a few tokens.
 *
 * '''It is the fast path only.''' It throws [[OnionParser.Fail]] -- an exception without a
 * stack trace -- on the first thing it cannot parse, and `Parsing` then re-parses the file
 * with the JavaCC parser, which owns error recovery, the expected-token sets and the syntax
 * hints. So a program this parser rejects costs one extra parse and gets exactly the
 * diagnostics it always got; what must never happen is the reverse, a program the JavaCC
 * parser rejects that this one accepts. The grammar file is the specification; each method
 * below is named after the production it implements and follows it clause by clause.
 *
 * Newlines. The grammar handles newline-terminated statements by switching the lexer
 * between two states -- `IN_STATEMENT`, where a newline is an `EOL` token, and `DEFAULT`,
 * where it is skipped -- at the same points `enterSection`/`enterDefault` are called below.
 * Here the lexer always emits `EOL` and a mode stack decides, at consumption time, whether
 * the parser sees them. That gives the same token sequence the state switches produce
 * without the buffered-lookahead artefacts the grammar has to work around.
 */
object OnionParser {

  /** Raised when the fast parser gives up; the caller falls back to the JavaCC parser. */
  class Fail(msg: String) extends RuntimeException(msg, null, false, false)

  private val theFail = new Fail(null)
  private val debug = java.lang.Boolean.getBoolean("onion.parser.debug")

  def parse(text: String): AST.CompilationUnit = new OnionParser(text).unit()

  private val boxedNames: Map[String, String] = Map(
    "Int" -> "java.lang.Integer", "Long" -> "java.lang.Long", "Double" -> "java.lang.Double",
    "Float" -> "java.lang.Float", "Boolean" -> "java.lang.Boolean", "Byte" -> "java.lang.Byte",
    "Short" -> "java.lang.Short", "Char" -> "java.lang.Character")

  private def isBasicKind(k: Int): Boolean =
    k == K.K_BYTE || k == K.K_SHORT || k == K.K_CHAR || k == K.K_INT || k == K.K_LONG ||
      k == K.K_FLOAT || k == K.K_DOUBLE || k == K.K_BOOLEAN

  private def isModifierKind(k: Int): Boolean =
    k == K.K_FINAL || k == K.K_INTERNAL || k == K.K_VOLATILE || k == K.K_ABSTRACT ||
      k == K.K_SYNCHRONIZED || k == K.K_STATIC || k == K.K_OVERRIDE || k == K.K_SEALED

  private def modifierBit(k: Int): Int = k match {
    case K.K_FINAL => AST.M_FINAL
    case K.K_INTERNAL => AST.M_INTERNAL
    case K.K_VOLATILE => AST.M_VOLATILE
    case K.K_ABSTRACT => AST.M_ABSTRACT
    case K.K_SYNCHRONIZED => AST.M_SYNCHRONIZED
    case K.K_STATIC => AST.M_STATIC
    case K.K_OVERRIDE => AST.M_OVERRIDE
    case K.K_SEALED => AST.M_SEALED
    case _ => 0
  }

  private def isAssignOp(k: Int): Boolean =
    k == K.ASSIGN || k == K.ADDEQ || k == K.SUBEQ || k == K.MULEQ || k == K.DIVEQ || k == K.MODEQ ||
      k == K.ANDEQ || k == K.OREQ || k == K.XOREQ || k == K.LSHIFTEQ || k == K.RSHIFTEQ || k == K.URSHIFTEQ

  /** Whether a token of this kind can begin `type()`. */
  private def isTypeStart(k: Int): Boolean =
    k == K.PLUS || k == K.QUESTION || k == K.LPAREN || k == K.ID || k == K.QUOTED_ID || k == K.FQCN || isBasicKind(k)

  /** Whether a token of this kind can begin `term()` (an expression of any form). */
  private def isTermStart(k: Int): Boolean = k match {
    case K.K_IF | K.K_WHILE | K.K_FOR | K.K_SELECT | K.LBRACE | K.K_TRY | K.K_THROW | K.K_BREAK |
         K.K_CONTINUE | K.K_RETURN | K.K_SYNCHRONIZED | K.K_FOREACH |
         K.K_SUPER | K.ID | K.QUOTED_ID | K.FQCN | K.LBRACKET | K.LPAREN | K.K_DO | K.K_NEW | K.K_SELF | K.K_THIS |
         K.INTEGER | K.FLOAT | K.CHARACTER | K.STRING | K.MULTI_LINE_STRING |
         K.RE_STRING | K.FILE_STRING | K.HTTP_STRING | K.SCHEME_STRING |
         K.K_TRUE | K.K_FALSE | K.K_NULL | K.PLUS | K.MINUS | K.NOT | K.BN => true
    case k => isBasicKind(k)
  }

  private def unescapeChar(c: Char): Char = c match {
    case 'n' => '\n'
    case 't' => '\t'
    case 'b' => '\b'
    case 'r' => '\r'
    case 'f' => '\f'
    case other => other // \\ \' \" \# and anything else map to themselves
  }

  private def unescape(s: String): String = {
    val b = new java.lang.StringBuilder(s.length)
    var i = 0
    val len = s.length
    while (i < len) {
      val c = s.charAt(i)
      if (c != '\\') b.append(c)
      else {
        i += 1
        if (i >= len) throw theFail
        b.append(unescapeChar(s.charAt(i)))
      }
      i += 1
    }
    b.toString
  }

  private def findInterpStart(str: String, from: Int): Int = {
    var pos = from
    while (true) {
      val idx = str.indexOf("#{", pos)
      if (idx == -1) return -1
      var slashes = 0
      var k = idx - 1
      while (k >= 0 && str.charAt(k) == '\\') { slashes += 1; k -= 1 }
      if (slashes % 2 == 0) return idx
      pos = idx + 1
    }
    -1
  }

  /** The (line, column) in the enclosing file at which an interpolated expression starts. */
  private def interpolationOrigin(str: String, exprStartInStr: Int, loc: Location, quoteLen: Int): (Int, Int) = {
    var nlCount = 0
    var lastNl = -1
    var i = 0
    while (i < exprStartInStr) {
      if (str.charAt(i) == '\n') { nlCount += 1; lastNl = i }
      i += 1
    }
    if (nlCount == 0) (loc.line, loc.column + quoteLen + exprStartInStr)
    else (loc.line + nlCount, exprStartInStr - lastNl)
  }

  private def parseIntLiteral(content: String): Int = {
    try {
      val hex = content.length > 2 && content.charAt(0) == '0' && (content.charAt(1) == 'x' || content.charAt(1) == 'X')
      val bin = content.length > 2 && content.charAt(0) == '0' && (content.charAt(1) == 'b' || content.charAt(1) == 'B')
      val lv =
        if (hex) java.lang.Long.parseLong(content.substring(2), 16)
        else if (bin) java.lang.Long.parseLong(content.substring(2), 2)
        else java.lang.Long.parseLong(content)
      if (hex || bin) { if (lv < 0L || lv > 0xFFFFFFFFL) throw theFail }
      else if (lv < Integer.MIN_VALUE || lv > 2147483648L) throw theFail
      lv.toInt
    } catch { case _: NumberFormatException => throw theFail }
  }

  private def parseLongLiteral(content: String): Long = {
    try {
      val hex = content.matches("0[xX].*")
      val bin = content.matches("0[bB].*")
      val bi =
        if (hex) new java.math.BigInteger(content.substring(2), 16)
        else if (bin) new java.math.BigInteger(content.substring(2), 2)
        else new java.math.BigInteger(content)
      if (hex || bin) { if (bi.signum() < 0 || bi.bitLength() > 64) throw theFail }
      else {
        val min = java.math.BigInteger.valueOf(Long.MinValue)
        val maxPlus1 = java.math.BigInteger.valueOf(Long.MaxValue).add(java.math.BigInteger.ONE)
        if (bi.compareTo(min) < 0 || bi.compareTo(maxPlus1) > 0) throw theFail
      }
      bi.longValue()
    } catch { case _: NumberFormatException => throw theFail }
  }
}

/**
 * @param lineBase added to every line number; `colBase` is added to the columns of the first
 *                 line only. Together they place the tokens of an interpolated expression at
 *                 their position in the enclosing file (the grammar pads the text instead).
 */
final class OnionParser(text: String, lineBase: Int = 0, colBase: Int = 0) {
  import OnionParser.*

  // ------------------------------------------------------------------ tokens

  private val lexer = new OnionLexer(text)
  lexer.SwitchTo(1) // IN_STATEMENT: every newline is an EOL token; visibility is decided below

  private var buf = new Array[Token](if (lineBase == 0 && colBase == 0) 256 else 16)
  private var len = 0 // tokens lexed so far
  private var pos = 0                 // index in buf of the next unconsumed raw token
  private var last: Token = null      // the last consumed token (JavaCC's `token`)
  private var lexedEof = false
  private var eolSignificant = false  // DEFAULT state at the start, as in the grammar
  private var cur: Token = null       // the next visible token, when already determined
  private val modeStack = new ArrayBuffer[Boolean](16)

  /** The give-up signal; with -Donion.parser.debug=true it says where. */
  private def fail: Fail =
    if (debug) new Fail(s"at ${peek(1).beginLine}:${peek(1).beginColumn} next=<${peek(1).image}> kind=${peek(1).kind} last=<${if (last == null) "" else last.image}> " +
      new Throwable().getStackTrace.drop(1).take(4).map(_.getMethodName).mkString("<-"))
    else theFail

  /** `needsBodyRewrite` of the unit: set when a do-expression or trait method call is parsed. */
  var needsBodyRewrite = false

  private val assignedScopes = new ArrayBuffer[java.util.HashSet[String]](16)
  private val unitAssigned = new java.util.HashSet[String]()
  private def leaveAssignScopeIntoUnit(): Unit = {
    val s = assignedScopes.remove(assignedScopes.length - 1)
    if (s != null) unitAssigned.addAll(s)
  }

  private def rawAt(i: Int): Token = if (i < len) buf(i) else lexAhead(i)

  private def lexAhead(i: Int): Token = {
    while (i >= len && !lexedEof) {
      val t = lexer.getNextToken()
      if (len == buf.length) buf = java.util.Arrays.copyOf(buf, len * 2)
      buf(len) = t
      len += 1
      if (t.kind == K.EOF) lexedEof = true
    }
    if (i < len) buf(i) else buf(len - 1)
  }

  /** The k-th (1-based) upcoming token as the parser sees it: EOLs skipped unless significant. */
  private def peek(k: Int): Token = {
    if (k == 1) {
      if (cur == null) cur = peekUncached(1)
      return cur
    }
    peekUncached(k)
  }

  private def peekUncached(k: Int): Token = {
    if (eolSignificant) rawAt(pos + k - 1)
    else {
      while (rawAt(pos).kind == K.EOL) pos += 1 // invisible here, and enterSection() skips them too
      if (k == 1) return rawAt(pos)
      var i = pos
      var n = 0
      while (true) {
        val t = rawAt(i)
        if (t.kind != K.EOL || t.kind == K.EOF) {
          n += 1
          if (n == k) return t
        }
        if (t.kind == K.EOF) return t
        i += 1
      }
      null
    }
  }

  private def kind(k: Int): Int = peek(k).kind
  private def image(k: Int): String = peek(k).image

  private def next(): Token = {
    if (!eolSignificant) while (rawAt(pos).kind == K.EOL) pos += 1
    val t = rawAt(pos)
    if (t.kind != K.EOF) pos += 1
    last = t
    cur = null
    t
  }

  private def expect(k: Int): Token = {
    if (kind(1) != k) throw fail
    next()
  }

  private def expectImage(s: String): Token = {
    if (image(1) != s) throw fail
    next()
  }

  private def accept(k: Int): Boolean = if (kind(1) == k) { next(); true } else false

  /** `eols()`: swallow visible EOL tokens. */
  private def eols(): Unit = if (eolSignificant) while (rawAt(pos).kind == K.EOL) { pos += 1; cur = null }

  private def enterState(significant: Boolean): Unit = { modeStack += eolSignificant; eolSignificant = significant; cur = null }
  private def leaveState(): Unit = { eolSignificant = modeStack.remove(modeStack.length - 1); cur = null }
  /**
   * `enterSection()` of the grammar. The JavaCC parser decides which production to enter by
   * peeking at the next token, so that token is already lexed -- in the enclosing state -- when
   * the production switches to IN_STATEMENT; a newline in front of it was therefore skipped.
   * Skipping the EOLs before the next token reproduces that.
   */
  private def enterSection(): Unit = { enterState(true); eols() }
  private def leaveSection(): Unit = leaveState()
  private def enterDefault(): Unit = enterState(false)
  private def leaveDefault(): Unit = leaveState()

  /** A raw peek that skips EOLs whatever the mode (the continuation helpers of the grammar). */
  private def rawKindSkippingEol(startOffset: Int): (Int, Int) = {
    var i = pos + startOffset
    while (rawAt(i).kind == K.EOL) i += 1
    (rawAt(i).kind, i)
  }

  // ---------------------------------------------------------------- speculation

  private final class Mark(val pos: Int, val last: Token, val sig: Boolean, val depth: Int)
  private def mark(): Mark = new Mark(pos, last, eolSignificant, modeStack.length)
  private def reset(m: Mark): Unit = {
    pos = m.pos; last = m.last; eolSignificant = m.sig; cur = null
    while (modeStack.length > m.depth) modeStack.remove(modeStack.length - 1)
  }

  /** Whether `body` parses from here; the cursor is restored either way. */
  private inline def looksLike(inline body: Unit): Boolean = {
    val m = mark()
    val ok = try { body; true } catch { case _: Fail => false }
    reset(m)
    ok
  }

  // ---------------------------------------------------------------- helpers

  private def p(t: Token): Location = {
    if (lineBase == 0 && colBase == 0) Location(t.beginLine, t.beginColumn, t.endLine, t.endColumn)
    else {
      val bc = if (t.beginLine == 1) t.beginColumn + colBase else t.beginColumn
      val ec = if (t.endLine == 1) t.endColumn + colBase else t.endColumn
      Location(t.beginLine + lineBase, bc, t.endLine + lineBase, ec)
    }
  }
  private def through(start: Token): Location = p(start).spanningTo(p(last))
  private def span(from: AST.Expression, to: AST.Expression): Location =
    if (from == null || from.location == null) { if (to == null) null else to.location }
    else if (to == null || to.location == null) from.location
    else from.location.spanningTo(to.location)

  private def c(t: Token): String = {
    val s = t.image
    if (s.length >= 2 && s.startsWith("`") && s.endsWith("`")) s.substring(1, s.length - 1) else s
  }

  private def la(s: String): Boolean = image(1) == s
  private def la2(s: String): Boolean = image(2) == s

  // The sets are allocated on first use: most blocks assign and capture nothing.
  private def enterAssignScope(): Unit = assignedScopes += null
  private def addTo(scopes: ArrayBuffer[java.util.HashSet[String]], i: Int, name: String): Unit = {
    var s = scopes(i)
    if (s == null) { s = new java.util.HashSet[String](); scopes(i) = s }
    s.add(name)
  }
  private def addAllTo(scopes: ArrayBuffer[java.util.HashSet[String]], i: Int, names: java.util.Set[String]): Unit =
    if (names != null && !names.isEmpty) {
      var s = scopes(i)
      if (s == null) { s = new java.util.HashSet[String](); scopes(i) = s }
      s.addAll(names)
    }
  private def leaveAssignScope(): Set[String] = {
    val s = assignedScopes.remove(assignedScopes.length - 1)
    if (assignedScopes.nonEmpty) addAllTo(assignedScopes, assignedScopes.length - 1, s)
    if (s == null) Set.empty else AST.toScalaSet(s)
  }

  private def noteAssigned(target: AST.Expression): Unit = target match {
    case id: AST.Id if assignedScopes.nonEmpty => addTo(assignedScopes, assignedScopes.length - 1, id.name)
    case _ =>
  }

  private def isId(k: Int): Boolean = k == K.ID || k == K.QUOTED_ID

  private def id(): Token = { val k = kind(1); if (isId(k)) next() else throw fail }

  private def importId(): Token = {
    val k = kind(1)
    if (isId(k) || k == K.K_LONG || k == K.K_INT || k == K.K_SHORT || k == K.K_BYTE || k == K.K_CHAR ||
        k == K.K_FLOAT || k == K.K_DOUBLE || k == K.K_BOOLEAN) next() else throw fail
  }

  private def eos(): Unit = {
    val k = kind(1)
    if (k == K.SEMI || k == K.EOL) next()
    else if (k != K.EOF) throw fail
    leaveSection()
    eols()
  }

  private def eosOrBlockEnd(): Unit = {
    val k = kind(1)
    if (k == K.SEMI || k == K.EOL) { next(); leaveSection(); eols() }
    else if (k == K.EOF) { leaveSection(); eols() }
    else if (k == K.RBRACE) leaveSection()
    else throw fail
  }

  // ---------------------------------------------------------------- unit level

  def unit(): AST.CompilationUnit = {
    val module = if (kind(1) == K.K_MODULE) moduleDecl() else null
    val imports = if (kind(1) == K.K_IMPORT) importDecl() else null
    val tops = new ArrayBuffer[AST.Toplevel]()
    while (kind(1) != K.EOF) tops += topLevel()
    if (tops.isEmpty) throw fail // the grammar demands at least one top-level element
    AST.CompilationUnit(new Location(1, 1), null, module, imports, tops.toList, needsBodyRewrite, AST.toScalaSet(unitAssigned))
  }

  private def moduleDecl(): AST.ModuleDeclaration = {
    enterSection()
    val t1 = expect(K.K_MODULE)
    val sb = new java.lang.StringBuilder
    sb.append(c(id()))
    while (kind(1) == K.DOT) { next(); sb.append('.').append(c(id())) }
    eos()
    AST.ModuleDeclaration(p(t1), sb.toString)
  }

  private def importDecl(): AST.ImportClause = {
    val t = expect(K.K_IMPORT)
    expect(K.LBRACE)
    val imports = new ArrayBuffer[(String, String)]()
    val statics = new ArrayBuffer[(String, String)]()
    while (kind(1) != K.RBRACE) {
      enterSection()
      val sb = new java.lang.StringBuilder
      // (import_id ".")+ : at least one dotted prefix
      var n = importId()
      if (kind(1) != K.DOT) throw fail
      var more = true
      while (more) {
        expect(K.DOT)
        sb.append(c(n)).append('.')
        if (kind(1) == K.STAR) { n = next(); more = false }
        else {
          n = importId()
          more = kind(1) == K.DOT
        }
      }
      var s = c(n)
      sb.append(s)
      var staticMethod: Token = null
      if (kind(1) == K.COLON2) {
        next()
        staticMethod = if (kind(1) == K.STAR) next() else importId()
      }
      if (kind(1) == K.K_AS) { next(); s = c(id()) }
      eosOrBlockEnd()
      if (staticMethod != null) statics += ((c(staticMethod), sb.toString))
      else imports += ((s, sb.toString))
    }
    expect(K.RBRACE)
    AST.ImportClause(p(t), imports.toList, statics.toList)
  }

  private def topLevel(): AST.Toplevel = {
    val k = kind(1)
    val img = image(1)
    if (img == "break" || img == "continue") return topLevelElement()
    if (k == K.ID && img == "tool" && kind(2) == K.ID && image(3) == "(") return toolDecl()
    if (k == K.ID && img == "example" && (kind(2) == K.LBRACE || (kind(2) == K.ID && kind(3) == K.LBRACE))) return topLevelExample()
    if (k == K.K_EXTENSION) return extensionDecl()
    if (k == K.K_INSTANCE) return instanceDecl()
    val declStart = isModifierKind(k) || k == K.K_TYPE || k == K.K_CLASS || k == K.K_INTERFACE || k == K.K_TRAIT ||
      k == K.K_RECORD || k == K.K_ENUM || k == K.K_DEF || k == K.ANNOTATION
    if (!declStart) return topLevelElement()
    val mset = if (isModifierKind(k)) modifiers() else 0
    kind(1) match {
      case K.K_TYPE => typeAliasDecl(mset)
      case K.K_INTERFACE | K.K_TRAIT | K.K_CLASS | K.K_RECORD | K.K_ENUM => typeDecl(mset)
      case K.K_DEF | K.ANNOTATION => funDecl(mset)
      case K.K_VAL | K.K_VAR => varDecl(mset)
      case _ => throw fail
    }
  }

  private def topLevelElement(): AST.BlockElement = {
    enterAssignScope()
    val e = blockElement()
    leaveAssignScopeIntoUnit()
    e
  }

  private def extensionDecl(): AST.ExtensionDeclaration = {
    val t = expect(K.K_EXTENSION)
    val receiver = typ()
    expect(K.LBRACE)
    val methods = new ArrayBuffer[AST.MethodDeclaration]()
    while (kind(1) != K.RBRACE) {
      val mset = if (isModifierKind(kind(1))) modifiers() else 0
      methods += methodDecl(mset)
    }
    expect(K.RBRACE)
    AST.ExtensionDeclaration(p(t), receiver, methods.toList)
  }

  private def instanceDecl(): AST.InstanceDeclaration = {
    val t = expect(K.K_INSTANCE)
    val traitType = typ()
    expect(K.LBRACE)
    val methods = new ArrayBuffer[AST.MethodDeclaration]()
    while (kind(1) != K.RBRACE) {
      val mset = if (isModifierKind(kind(1))) modifiers() else 0
      methods += methodDecl(mset)
    }
    expect(K.RBRACE)
    accept(K.SEMI)
    AST.InstanceDeclaration(p(t), AST.M_PUBLIC, traitType, methods.toList)
  }

  private def varDecl(modifiers: Int): AST.GlobalVariableDeclaration = {
    enterSection()
    val keyword = if (kind(1) == K.K_VAL || kind(1) == K.K_VAR) next() else throw fail
    val name = id()
    expect(K.COLON)
    val ty = typ()
    val e = if (accept(K.ASSIGN)) term() else null
    eos()
    val mods = if (keyword.kind == K.K_VAL) modifiers | AST.M_FINAL else modifiers
    AST.GlobalVariableDeclaration(p(keyword), mods, c(name), ty, e)
  }

  private def typeAliasDecl(modifiers: Int): AST.TypeAliasDeclaration = {
    enterSection()
    val t = expect(K.K_TYPE)
    val name = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    expect(K.ASSIGN)
    val target = typ()
    eos()
    AST.TypeAliasDeclaration(p(t), modifiers, c(name), tparams, target)
  }

  private def funDecl(modifiers: Int): AST.FunctionDeclaration = {
    val anns = annotations()
    val t1 = expect(K.K_DEF)
    val t2 = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    val args = if (accept(K.LPAREN)) { val a = argsList(); expect(K.RPAREN); a } else Nil
    val ty = if (accept(K.COLON)) returnType() else null
    val throwsTypes = if (kind(1) == K.K_THROWS) throwsClause() else Nil
    kind(1) match {
      case K.LBRACE =>
        val b = block()
        AST.FunctionDeclaration(p(t1), modifiers, c(t2), args, ty, b, tparams, throwsTypes, anns)
      case K.ASSIGN =>
        enterSection()
        next()
        eols()
        enterAssignScope()
        val e = term()
        eos()
        AST.FunctionDeclaration(p(t1), modifiers, c(t2), args, ty,
          AST.BlockExpression(p(t1), List(AST.ReturnExpression(p(t1), e)), leaveAssignScope()), tparams, throwsTypes, anns)
      case _ =>
        enterSection()
        eos()
        AST.FunctionDeclaration(p(t1), modifiers, c(t2), args, ty, null, tparams, throwsTypes, anns)
    }
  }

  private def throwsClause(): List[AST.TypeNode] = {
    expect(K.K_THROWS)
    val types = new ArrayBuffer[AST.TypeNode]()
    types += classType()
    while (accept(K.COMMA)) types += classType()
    types.toList
  }

  private def toolDecl(): AST.FunctionDeclaration = {
    val t1 = expect(K.ID)
    val t2 = id()
    val anns = new ArrayBuffer[AST.Annotation]()
    anns += AST.Annotation(p(t1), "onion.tool")
    expect(K.LPAREN)
    val args = argsList()
    expect(K.RPAREN)
    val ty = if (accept(K.COLON)) returnType() else null
    if (image(1) == "requires" && image(2) == "{") {
      expect(K.ID)
      expect(K.LBRACE)
      eols()
      anns += AST.Annotation(p(t1), "requires:" + capability())
      while (accept(K.COMMA)) { eols(); anns += AST.Annotation(p(t1), "requires:" + capability()) }
      eols()
      expect(K.RBRACE)
    }
    val b = block()
    AST.FunctionDeclaration(p(t1), 0, c(t2), args, ty, b, Nil, Nil, anns.toList)
  }

  private def topLevelExample(): AST.TopLevelExample = {
    val t = expect(K.ID)
    val n = if (isId(kind(1))) id() else null
    val b = block()
    AST.topLevelExample(p(t), if (n == null) null else c(n), b)
  }

  private def capability(): String = {
    val n = id()
    if (accept(K.LPAREN)) { val a = id(); expect(K.RPAREN); n.image + "(" + a.image + ")" }
    else n.image
  }

  private def typeDecl(modifiers: Int): AST.TypeDeclaration = kind(1) match {
    case K.K_INTERFACE => interfaceDecl(modifiers)
    case K.K_TRAIT => traitDecl(modifiers)
    case K.K_CLASS => classDecl(modifiers)
    case K.K_RECORD => recordDecl(modifiers)
    case K.K_ENUM => enumDecl(modifiers)
    case _ => throw fail
  }

  // ---------------------------------------------------------------- blocks & modifiers

  private def block(): AST.BlockExpression = {
    val t = expect(K.LBRACE)
    enterAssignScope()
    eols()
    val elements = if (kind(1) != K.RBRACE) blockElements() else Nil
    eols()
    expect(K.RBRACE)
    AST.BlockExpression(p(t), elements, leaveAssignScope())
  }

  private def blockElementFollows(): Boolean = {
    val k = kind(1)
    k != K.RBRACE && k != K.EOF && k != K.K_CASE && k != K.K_ELSE
  }

  private def blockElements(): List[AST.BlockElement] = {
    val elements = new ArrayBuffer[AST.BlockElement]()
    if (!blockElementFollows()) throw fail
    while (blockElementFollows()) {
      elements += blockElement()
      eols()
    }
    elements.toList
  }

  private def modifiers(): Int = {
    var mset = 0
    val k0 = kind(1)
    if (!isModifierKind(k0)) throw fail
    next()
    mset = modifierBit(k0)
    var going = true
    while (going) {
      val k = kind(1)
      if (isModifierKind(k) && (modifierBit(k) & mset) == 0) { next(); mset |= modifierBit(k) }
      else going = false
    }
    mset
  }

  private def isMemberStart(k: Int): Boolean =
    isModifierKind(k) || k == K.K_VAL || k == K.K_VAR || k == K.K_FORWARD || k == K.K_DEF || k == K.ANNOTATION

  private def defaultSection(): AST.AccessSection = {
    val members = new ArrayBuffer[AST.MemberDeclaration]()
    var mset = if (isModifierKind(kind(1))) modifiers() else 0
    val first = memberDecl(mset)
    members += first
    val location = first.location
    while (isMemberStart(kind(1))) {
      mset = if (isModifierKind(kind(1))) modifiers() else 0
      members += memberDecl(mset)
    }
    AST.AccessSection(location, AST.M_PRIVATE, members.toList)
  }

  private def accessSection(): AST.AccessSection = {
    val t = next()
    val sectionType = t.kind match {
      case K.K_PUBLIC => AST.M_PUBLIC
      case K.K_PROTECTED => AST.M_PROTECTED
      case K.K_PRIVATE => AST.M_PRIVATE
      case _ => throw fail
    }
    expect(K.COLON)
    val members = new ArrayBuffer[AST.MemberDeclaration]()
    while (isMemberStart(kind(1))) {
      val mset = if (isModifierKind(kind(1))) modifiers() else 0
      members += memberDecl(mset)
    }
    AST.AccessSection(p(t), sectionType, members.toList)
  }

  private def isAccessSectionStart(k: Int): Boolean = k == K.K_PUBLIC || k == K.K_PROTECTED || k == K.K_PRIVATE

  private def memberDecl(mset: Int): AST.MemberDeclaration = kind(1) match {
    case K.K_VAL | K.K_VAR => fieldDecl(mset)
    case K.K_FORWARD => delegateDecl(mset)
    case K.K_DEF if image(2) == "this" => constructorDecl(mset)
    case K.K_DEF | K.ANNOTATION => methodDecl(mset)
    case _ => throw fail
  }

  // ---------------------------------------------------------------- type declarations

  private def classDecl(mset: Int): AST.ClassDeclaration = {
    val t1 = expect(K.K_CLASS)
    val t2 = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    var primaryParams: ArrayBuffer[AST.Argument] = null
    var primaryKinds: ArrayBuffer[Int] = null
    if (accept(K.LPAREN)) {
      primaryParams = new ArrayBuffer[AST.Argument]()
      primaryKinds = new ArrayBuffer[Int]()
      def one(): Unit = {
        var k = 0
        if (kind(1) == K.K_VAL) { next(); k = 1 } else if (kind(1) == K.K_VAR) { next(); k = 2 }
        primaryParams += argument()
        primaryKinds += k
      }
      if (kind(1) != K.RPAREN) {
        one()
        while (accept(K.COMMA)) one()
      }
      expect(K.RPAREN)
    }
    var ty1: AST.TypeNode = null
    var superArgs: List[AST.Expression] = null
    if (accept(K.K_EXTENDS)) {
      ty1 = typ()
      if (kind(1) == K.LPAREN) { next(); superArgs = terms(); expect(K.RPAREN) }
    }
    val ty2s = new ArrayBuffer[AST.TypeNode]()
    if (kind(1) == K.ID && image(1) == "conforms") {
      next()
      ty2s += typ()
      while (accept(K.COMMA)) ty2s += typ()
    }
    var sec1: AST.AccessSection = null
    val sec2s = new ArrayBuffer[AST.AccessSection]()
    if (kind(1) == K.LBRACE) {
      next()
      if (kind(1) != K.RBRACE && !isAccessSectionStart(kind(1))) sec1 = defaultSection()
      while (isAccessSectionStart(kind(1))) sec2s += accessSection()
      expect(K.RBRACE)
      accept(K.SEMI)
    } else if (kind(1) == K.SEMI) next()
    val hasPrimary = primaryParams != null || superArgs != null
    if (primaryParams == null && superArgs != null) {
      primaryParams = new ArrayBuffer[AST.Argument]()
      primaryKinds = new ArrayBuffer[Int]()
    }
    if (primaryParams != null) {
      val members = new ArrayBuffer[AST.MemberDeclaration]()
      val assigns = new ArrayBuffer[AST.BlockElement]()
      val ctorArgs = new ArrayBuffer[AST.Argument]()
      var i = 0
      while (i < primaryParams.length) {
        val param = primaryParams(i)
        val k = primaryKinds(i)
        ctorArgs += param
        if (k != 0) {
          val fieldMods = if (k == 1) AST.M_FINAL else 0
          members += AST.FieldDeclaration(param.location, fieldMods, param.name, param.typeRef, null)
          assigns += AST.Assignment(param.location,
            AST.MemberSelection(param.location, AST.CurrentInstance(param.location), param.name),
            AST.Id(param.location, param.name))
        }
        i += 1
      }
      val superInits = if (superArgs != null) superArgs else Nil
      members += AST.ConstructorDeclaration(p(t1), 0, ctorArgs.toList, superInits,
        AST.BlockExpression(p(t1), assigns.toList, null), false, true, assigns.length)
      sec2s += AST.AccessSection(p(t1), AST.M_PUBLIC, members.toList)
    }
    AST.ClassDeclaration(p(t1), mset, c(t2), ty1, ty2s.toList, Option(sec1), sec2s.toList, tparams, hasPrimary)
  }

  private def recordDecl(mset: Int): AST.RecordDeclaration = {
    val t1 = expect(K.K_RECORD)
    val t2 = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    expect(K.LPAREN)
    val args = argsList()
    expect(K.RPAREN)
    var fromRaw: String = null
    if (kind(1) == K.ID && image(1) == "from" && kind(2) == K.RE_STRING) {
      next()
      val rt = next()
      fromRaw = rt.image.substring(3, rt.image.length - 1)
    }
    val derives = new ArrayBuffer[String]()
    if (kind(1) == K.ID && image(1) == "derive" && kind(2) == K.NOT) {
      next(); next()
      expect(K.LPAREN)
      derives += c(id())
      while (accept(K.COMMA)) derives += c(id())
      expect(K.RPAREN)
    }
    val laws = new ArrayBuffer[AST.LawClause]()
    val examples = new ArrayBuffer[AST.ExampleClause]()
    val shapes = new ArrayBuffer[AST.ShapeClause]()
    var going = true
    while (going) {
      if (kind(1) == K.ID && image(1) == "law" && kind(2) == K.ID) {
        next()
        val lt = expect(K.ID)
        expect(K.LPAREN)
        val largs = argsList()
        expect(K.RPAREN)
        val lb = block()
        laws += AST.LawClause(p(lt), c(lt), largs, lb)
      } else if (kind(1) == K.ID && image(1) == "example" && (kind(2) == K.LBRACE || kind(2) == K.ID)) {
        val et = expect(K.ID)
        val enm = if (kind(1) == K.ID) expect(K.ID) else null
        val eb = block()
        examples += AST.exampleClause(p(et), if (enm == null) null else c(enm), eb)
      } else if (kind(1) == K.ID && image(1) == "shape" && kind(2) == K.ID) {
        next()
        val st = expect(K.ID)
        expect(K.ASSIGN)
        if (kind(1) == K.RE_STRING) {
          val srt = next()
          shapes += AST.regexShapeClause(p(st), c(st), srt.image.substring(3, srt.image.length - 1))
        } else {
          val srt = expect(K.ID)
          shapes += AST.formatShapeClause(p(st), c(st), c(srt))
        }
      } else going = false
    }
    val superTypes = new ArrayBuffer[AST.TypeNode]()
    if (kind(1) == K.ID && image(1) == "conforms") {
      next()
      superTypes += typ()
      while (accept(K.COMMA)) superTypes += typ()
    }
    val sections = new ArrayBuffer[AST.AccessSection]()
    if (accept(K.LBRACE)) {
      while (isAccessSectionStart(kind(1))) sections += accessSection()
      expect(K.RBRACE)
    }
    accept(K.SEMI)
    AST.recordDeclaration(p(t1), mset, c(t2), tparams, args, superTypes.toList, fromRaw, derives.toList,
      laws.toList, examples.toList, sections.toList, shapes.toList)
  }

  private def interfaceDecl(mset: Int): AST.InterfaceDeclaration = {
    val start = expect(K.K_INTERFACE)
    val name = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    val superTypes = new ArrayBuffer[AST.TypeNode]()
    if (kind(1) == K.ID && image(1) == "conforms") {
      next()
      superTypes += typ()
      while (accept(K.COMMA)) superTypes += typ()
    }
    val signatures = new ArrayBuffer[AST.MethodDeclaration]()
    if (kind(1) == K.LBRACE) {
      next()
      while (kind(1) == K.K_DEF) signatures += interfaceMethodDecl()
      expect(K.RBRACE)
      accept(K.SEMI)
    } else if (kind(1) == K.SEMI) next()
    AST.InterfaceDeclaration(p(start), mset, c(name), superTypes.toList, signatures.toList, tparams)
  }

  private def traitDecl(mset: Int): AST.InterfaceDeclaration = {
    val start = expect(K.K_TRAIT)
    val name = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    val superTypes = new ArrayBuffer[AST.TypeNode]()
    if (kind(1) == K.ID && image(1) == "conforms") {
      next()
      superTypes += typ()
      while (accept(K.COMMA)) superTypes += typ()
    }
    expect(K.LBRACE)
    val signatures = new ArrayBuffer[AST.MethodDeclaration]()
    while (kind(1) == K.K_DEF) signatures += interfaceMethodDecl()
    expect(K.RBRACE)
    accept(K.SEMI)
    AST.InterfaceDeclaration(p(start), mset, c(name), superTypes.toList, signatures.toList, tparams)
  }

  private def enumDecl(mset: Int): AST.EnumDeclaration = {
    val start = expect(K.K_ENUM)
    val name = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    val params = if (accept(K.LPAREN)) { val a = argsList(); expect(K.RPAREN); a } else Nil
    expect(K.LBRACE)
    val constants = new ArrayBuffer[AST.EnumConstant]()
    val sections = new ArrayBuffer[AST.AccessSection]()
    if (la("case")) {
      while (la("case")) {
        constants += enumCaseConstant()
        accept(K.SEMI)
      }
      while (isAccessSectionStart(kind(1))) sections += accessSection()
    } else {
      constants += enumConstant()
      while (la(",") && !la2("}")) { next(); constants += enumConstant() }
      if (la(",") && la2("}")) next()
      while (isAccessSectionStart(kind(1))) sections += accessSection()
    }
    expect(K.RBRACE)
    accept(K.SEMI)
    AST.EnumDeclaration(p(start), mset, c(name), params, constants.toList, sections.toList, tparams)
  }

  private def enumCaseConstant(): AST.EnumConstant = {
    expect(K.K_CASE)
    val name = id()
    val fields = if (accept(K.LPAREN)) { val a = argsList(); expect(K.RPAREN); a } else Nil
    AST.enumCaseConstant(p(name), c(name), fields)
  }

  private def enumConstant(): AST.EnumConstant = {
    val name = id()
    val args = if (accept(K.LPAREN)) { val a = terms(); expect(K.RPAREN); a } else Nil
    AST.enumConstant(p(name), c(name), args)
  }

  private def constructorDecl(mset: Int): AST.ConstructorDeclaration = {
    expect(K.K_DEF)
    val t = expect(K.K_THIS)
    val args = if (accept(K.LPAREN)) { val a = argsList(); expect(K.RPAREN); a } else Nil
    var params: List[AST.Expression] = Nil
    var selfDelegation = false
    if (accept(K.COLON)) {
      expect(K.K_THIS)
      expect(K.LPAREN)
      params = terms()
      expect(K.RPAREN)
      selfDelegation = true
    }
    val b = block()
    AST.ConstructorDeclaration(p(t), mset, args, params, b, selfDelegation, false, 0)
  }

  private def annotations(): List[AST.Annotation] = {
    if (kind(1) != K.ANNOTATION) return Nil
    val anns = new ArrayBuffer[AST.Annotation]()
    while (kind(1) == K.ANNOTATION) {
      val t = next()
      anns += AST.Annotation(p(t), t.image.substring(1))
      eols()
    }
    anns.toList
  }

  private def methodDecl(mset: Int): AST.MethodDeclaration = {
    val anns = annotations()
    enterSection()
    expect(K.K_DEF)
    val t = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    val args = if (accept(K.LPAREN)) { val a = argsList(); expect(K.RPAREN); a } else Nil
    val ty = if (accept(K.COLON)) returnType() else null
    val throwsTypes = if (kind(1) == K.K_THROWS) throwsClause() else Nil
    kind(1) match {
      case K.ASSIGN =>
        next()
        eols()
        enterAssignScope()
        val e = term()
        eosOrBlockEnd()
        AST.MethodDeclaration(p(t), mset, c(t), args, ty,
          AST.BlockExpression(p(t), List(AST.ReturnExpression(p(t), e)), leaveAssignScope()), tparams, throwsTypes, anns)
      case K.LBRACE =>
        leaveSection()
        val b = block()
        AST.MethodDeclaration(p(t), mset, c(t), args, ty, b, tparams, throwsTypes, anns)
      case _ =>
        eosOrBlockEnd()
        AST.MethodDeclaration(p(t), mset, c(t), args, ty, null, tparams, throwsTypes, anns)
    }
  }

  private def interfaceMethodDecl(): AST.MethodDeclaration = {
    enterSection()
    expect(K.K_DEF)
    val n = id()
    val tparams = if (kind(1) == K.LBRACKET) typeParams() else Nil
    val args = if (accept(K.LPAREN)) { val a = argsList(); expect(K.RPAREN); a } else Nil
    val ty = if (accept(K.COLON)) returnType() else null
    val throwsTypes = if (kind(1) == K.K_THROWS) throwsClause() else Nil
    kind(1) match {
      case K.ASSIGN =>
        next()
        eols()
        enterAssignScope()
        val e = term()
        eosOrBlockEnd()
        AST.MethodDeclaration(p(n), AST.M_PUBLIC, c(n), args, ty,
          AST.BlockExpression(p(n), List(AST.ReturnExpression(p(n), e)), leaveAssignScope()), tparams, throwsTypes, Nil)
      case K.LBRACE =>
        leaveSection()
        val b = block()
        AST.MethodDeclaration(p(n), AST.M_PUBLIC, c(n), args, ty, b, tparams, throwsTypes, Nil)
      case _ =>
        eosOrBlockEnd()
        AST.MethodDeclaration(p(n), AST.M_PUBLIC, c(n), args, ty, null, tparams, throwsTypes, Nil)
    }
  }

  // ---------------------------------------------------------------- types

  private def primitiveKind(k: Int): AST.PrimitiveTypeKind = k match {
    case K.K_BYTE => AST.KByte
    case K.K_SHORT => AST.KShort
    case K.K_CHAR => AST.KChar
    case K.K_INT => AST.KInt
    case K.K_LONG => AST.KLong
    case K.K_FLOAT => AST.KFloat
    case K.K_DOUBLE => AST.KDouble
    case K.K_BOOLEAN => AST.KBoolean
    case _ => throw fail
  }

  private def basicType(): AST.TypeNode = {
    val t = next()
    AST.TypeNode(p(t), AST.PrimitiveType(primitiveKind(t.kind)), false)
  }

  private def classType(): AST.TypeNode = {
    if (kind(1) == K.FQCN) { val n = next(); AST.TypeNode(p(n), AST.ReferenceType(n.image, true), false) }
    else { val n = id(); AST.TypeNode(p(n), AST.ReferenceType(c(n), false), false) }
  }

  private def dottedClassType(): AST.TypeNode = {
    var n = id()
    val sb = new java.lang.StringBuilder(c(n))
    if (kind(1) != K.DOT) throw fail
    while (kind(1) == K.DOT) { next(); n = id(); sb.append('.').append(c(n)) }
    AST.TypeNode(p(n), AST.ReferenceType(sb.toString, true), false)
  }

  private def boxedClassType(): AST.TypeNode = {
    if (!isBasicKind(kind(1))) throw fail
    val t = next()
    AST.TypeNode(p(t), AST.ReferenceType(boxedNames.getOrElse(t.image, t.image), true), false)
  }

  private def rawTypeDotContinues(): Boolean = {
    if (kind(1) != K.DOT || kind(2) != K.ID) return false
    kind(3) match {
      case K.DOT | K.LBRACKET | K.RBRACKET | K.ASSIGN | K.RPAREN | K.LPAREN | K.COMMA | K.SEMI | K.GT |
           K.LBRACE | K.QUESTION | K.ID | K.EOL | K.EOF => true
      case _ => false
    }
  }

  private def rawType(): AST.TypeNode = {
    val k = kind(1)
    if (isBasicKind(k)) basicType()
    else if (k == K.FQCN) { val n = next(); AST.TypeNode(p(n), AST.ReferenceType(n.image, true), false) }
    else {
      var n = id()
      val sb = new java.lang.StringBuilder(c(n))
      while (rawTypeDotContinues()) { next(); n = id(); sb.append('.').append(c(n)) }
      val name = sb.toString
      AST.TypeNode(p(n), AST.ReferenceType(name, name.contains(".")), false)
    }
  }

  private def typeParams(): List[AST.TypeParameter] = {
    expect(K.LBRACKET)
    val params = new ArrayBuffer[AST.TypeParameter]()
    def one(): Unit = {
      val name = id()
      val bound = if (accept(K.K_EXTENDS)) typ() else null
      val cons = new ArrayBuffer[AST.TypeNode]()
      if (accept(K.COLON)) {
        cons += classType()
        while (accept(K.PLUS)) cons += classType()
      }
      params += AST.TypeParameter(p(name), c(name), Option(bound), cons.toList)
    }
    one()
    while (accept(K.COMMA)) one()
    expect(K.RBRACKET)
    params.toList
  }

  private def typeArguments(): List[AST.TypeNode] = {
    expect(K.LBRACKET)
    eols()
    val args = new ArrayBuffer[AST.TypeNode]()
    args += typ()
    while (accept(K.COMMA)) { eols(); args += typ() }
    eols()
    expect(K.RBRACKET)
    args.toList
  }

  private def typ(): AST.TypeNode = {
    val t = typeImpl()
    if (kind(1) == K.ARROW) functionTypeTail(t) else t
  }

  private def typeNoArrow(): AST.TypeNode = typeImpl()

  private def typeImpl(): AST.TypeNode = {
    var isRelaxed = false
    if (kind(1) == K.PLUS) { next(); isRelaxed = true }
    var core: AST.TypeDescriptor = null
    var loc: Location = null
    kind(1) match {
      case K.QUESTION =>
        val start = next()
        loc = p(start)
        var upper: Option[AST.TypeDescriptor] = None
        var lower: Option[AST.TypeDescriptor] = None
        if (kind(1) == K.K_EXTENDS) { next(); upper = Some(typ().desc) }
        else if (kind(1) == K.K_SUPER) { next(); lower = Some(typ().desc) }
        core = AST.WildcardType(upper, lower)
      case K.LPAREN =>
        val start = next()
        eols()
        val fargs = new ArrayBuffer[AST.TypeDescriptor]()
        if (isTypeStart(kind(1))) {
          fargs += typ().desc
          while (accept(K.COMMA)) { eols(); fargs += typ().desc }
        }
        eols()
        expect(K.RPAREN)
        eols()
        expect(K.ARROW)
        eols()
        val r = returnType()
        core = AST.FunctionType(fargs.toList, r.desc)
        loc = p(start)
      case _ =>
        val cty = rawType()
        if (kind(1) == K.LBRACKET && isTypeStart(kind(2))) {
          next()
          val args = new ArrayBuffer[AST.TypeDescriptor]()
          args += typ().desc
          while (accept(K.COMMA)) args += typ().desc
          expect(K.RBRACKET)
          core = AST.ParameterizedType(cty.desc, args.toList)
        } else core = cty.desc
        loc = cty.location
    }
    var left = core
    var dims = true
    while (dims) {
      if (kind(1) == K.LBRACKET && kind(2) == K.RBRACKET) { next(); next(); left = AST.ArrayType(left) }
      else if (kind(1) == K.SAFE_INDEX && kind(2) == K.RBRACKET) { next(); next(); left = AST.ArrayType(AST.NullableType(left)) }
      else dims = false
    }
    if (kind(1) == K.QUESTION) { next(); left = AST.NullableType(left) }
    AST.TypeNode(loc, left, isRelaxed)
  }

  private def returnType(): AST.TypeNode =
    if (kind(1) == K.K_VOID) { val t = next(); AST.TypeNode(p(t), AST.PrimitiveType(AST.KVoid), false) } else typ()

  private def argument(): AST.Argument = {
    val t = id()
    expect(K.COLON)
    val ty = typ()
    val isVararg = accept(K.ELLIPSIS)
    val defaultValue = if (accept(K.ASSIGN)) { eols(); term() } else null
    AST.Argument(p(t), c(t), ty, defaultValue, isVararg)
  }

  private def argsList(): List[AST.Argument] = {
    if (!isId(kind(1))) return Nil
    val as = new ArrayBuffer[AST.Argument]()
    as += argument()
    while (accept(K.COMMA)) as += argument()
    as.toList
  }

  private def lambdaArgument(parenthesized: Boolean): AST.Argument = {
    val t = id()
    var ty: AST.TypeNode = null
    if (accept(K.COLON)) {
      ty = typeNoArrow()
      if (parenthesized && kind(1) == K.ARROW) ty = functionTypeTail(ty)
    }
    AST.Argument(p(t), c(t), ty, null, false)
  }

  private def functionTypeTail(arg: AST.TypeNode): AST.TypeNode = {
    expect(K.ARROW)
    eols()
    val r = typ()
    AST.TypeNode(arg.location, AST.FunctionType(List(arg.desc), r.desc), arg.isRelaxed)
  }

  private def lambdaArgs(parenthesized: Boolean): List[AST.Argument] = {
    if (!isId(kind(1))) return Nil
    val as = new ArrayBuffer[AST.Argument]()
    as += lambdaArgument(parenthesized)
    while (accept(K.COMMA)) as += lambdaArgument(parenthesized)
    as.toList
  }

  private def lambdaArgHead(): Unit = { id(); if (accept(K.COLON)) typ() }
  private def trailingLambdaArgHead(): Unit = { id(); if (accept(K.COLON)) typeNoArrow() }

  private def lambdaHead(): Unit = {
    expect(K.LPAREN)
    eols()
    if (isId(kind(1))) {
      lambdaArgHead()
      while (accept(K.COMMA)) { eols(); lambdaArgHead() }
    }
    eols()
    expect(K.RPAREN)
    eols()
    expect(K.ARROW)
  }

  private def trailingLambdaHead(): Unit = {
    expect(K.LBRACE)
    eols()
    if (isId(kind(1))) {
      trailingLambdaArgHead()
      while (accept(K.COMMA)) { eols(); trailingLambdaArgHead() }
    }
    eols()
    expect(K.ARROW)
  }

  private def delegateDecl(modifiers: Int): AST.DelegatedFieldDeclaration = {
    enterSection()
    val start = expect(K.K_FORWARD)
    val keyword = if (kind(1) == K.K_VAL || kind(1) == K.K_VAR) next() else throw fail
    val name = id()
    expect(K.COLON)
    val ty = typ()
    val init = if (accept(K.ASSIGN)) { eols(); term() } else null
    eosOrBlockEnd()
    val mods = if (keyword.kind == K.K_VAL) modifiers | AST.M_FINAL else modifiers
    AST.DelegatedFieldDeclaration(p(start), mods, c(name), ty, init)
  }

  private def fieldDecl(modifiers: Int): AST.FieldDeclaration = {
    enterSection()
    val keyword = if (kind(1) == K.K_VAL || kind(1) == K.K_VAR) next() else throw fail
    val name = id()
    expect(K.COLON)
    val ty = typ()
    val init = if (accept(K.ASSIGN)) { eols(); term() } else null
    eosOrBlockEnd()
    val mods = if (keyword.kind == K.K_VAL) modifiers | AST.M_FINAL else modifiers
    AST.FieldDeclaration(p(keyword), mods, c(name), ty, init)
  }

  // ---------------------------------------------------------------- statements

  private def blockElement(): AST.BlockElement = {
    if (kind(1) == K.ID && image(2) == ":" &&
        (image(3) == "while" || image(3) == "for" || image(3) == "foreach" || image(3) == "do")) {
      val lbl = id()
      expect(K.COLON)
      val lp: AST.Expression = kind(1) match {
        case K.K_WHILE => whileExpression()
        case K.K_FOR => forExpression()
        case K.K_FOREACH => foreachExpression()
        case K.K_DO if kind(2) == K.LBRACE => doWhileExpression()
        case _ => throw fail
      }
      return AST.LabeledLoop(p(lbl), c(lbl), lp)
    }
    kind(1) match {
      case K.K_VAL | K.K_VAR => localVarDeclaration()
      case K.K_IF => ifExpression()
      case K.K_WHILE => whileExpression()
      case K.K_FOR => forExpression()
      case K.K_SELECT => selectExpression()
      case K.LBRACE => block()
      case K.K_TRY => tryExpression()
      case K.K_THROW => terminatedThrow()
      case K.K_BREAK => terminatedBreak()
      case K.K_CONTINUE => terminatedContinue()
      case K.K_RETURN => terminatedReturn()
      case K.K_SYNCHRONIZED => synchronizedExpression()
      case K.K_FOREACH => foreachExpression()
      case K.K_DO if image(2) == "{" => doWhileExpression()
      case _ => expressionElement()
    }
  }

  private def localVarDeclaration(): AST.BlockElement = {
    enterSection()
    val keyword = next()
    val declMods = if (keyword.kind == K.K_VAL) AST.M_FINAL else if (keyword.kind == K.K_VAR) 0 else throw fail
    if (kind(1) == K.LPAREN) {
      next()
      val names = new ArrayBuffer[String]()
      names += c(id())
      if (kind(1) != K.COMMA) throw fail
      while (accept(K.COMMA)) names += c(id())
      expect(K.RPAREN)
      expect(K.ASSIGN)
      eols()
      val e = term()
      eosOrBlockEnd()
      AST.DestructuringDeclaration(p(keyword), declMods, names.toList, e)
    } else {
      val t = id()
      var ty: AST.TypeNode = null
      var e: AST.Expression = null
      if (accept(K.COLON)) {
        ty = typ()
        if (accept(K.ASSIGN)) { eols(); e = term() }
      } else {
        expect(K.ASSIGN)
        eols()
        e = term()
      }
      eosOrBlockEnd()
      AST.LocalVariableDeclaration(p(keyword), declMods, c(t), ty, e)
    }
  }

  private def sameLineLabel(t: Token): String = {
    val n = peek(1)
    if (n.kind == K.ID && n.beginLine == t.endLine) c(id()) else null
  }

  private def terminatedBreak(): AST.BreakExpression = {
    enterSection()
    val t = expect(K.K_BREAK)
    val l = sameLineLabel(t)
    eosOrBlockEnd()
    AST.BreakExpression(p(t), l)
  }

  private def breakExpression(): AST.BreakExpression = {
    val t = expect(K.K_BREAK)
    AST.BreakExpression(p(t), sameLineLabel(t))
  }

  private def terminatedContinue(): AST.ContinueExpression = {
    enterSection()
    val t = expect(K.K_CONTINUE)
    val l = sameLineLabel(t)
    eosOrBlockEnd()
    AST.ContinueExpression(p(t), l)
  }

  private def continueExpression(): AST.ContinueExpression = {
    val t = expect(K.K_CONTINUE)
    AST.ContinueExpression(p(t), sameLineLabel(t))
  }

  private def terminatedThrow(): AST.ThrowExpression = {
    enterSection()
    val t = expect(K.K_THROW)
    val e = term()
    eosOrBlockEnd()
    AST.ThrowExpression(p(t), e)
  }

  private def throwExpression(): AST.ThrowExpression = {
    val t = expect(K.K_THROW)
    AST.ThrowExpression(p(t), term())
  }

  private def resourceDecl(): AST.LocalVariableDeclaration = {
    val t = expect(K.K_VAL)
    eols()
    val name = expect(K.ID)
    val ty = if (accept(K.COLON)) typ() else null
    expect(K.ASSIGN)
    eols()
    val init = term()
    AST.LocalVariableDeclaration(p(t), 0, c(name), ty, init)
  }

  private def tryExpression(): AST.TryExpression = {
    val t = expect(K.K_TRY)
    var resources: List[AST.LocalVariableDeclaration] = Nil
    if (kind(1) == K.LPAREN && kind(2) == K.K_VAL) {
      next()
      val rs = new ArrayBuffer[AST.LocalVariableDeclaration]()
      rs += resourceDecl()
      while (accept(K.SEMI)) { eols(); rs += resourceDecl() }
      expect(K.RPAREN)
      resources = rs.toList
    }
    val b1 = block()
    val clauses = new ArrayBuffer[(AST.Argument, AST.BlockExpression)]()
    while (kind(1) == K.K_CATCH) {
      next()
      val a = argument()
      val extra = new ArrayBuffer[AST.TypeNode]()
      while (accept(K.BAR)) extra += typ()
      val b2 = block()
      clauses += ((a, b2))
      for (ty2 <- extra) clauses += ((AST.Argument(a.location, a.name, ty2, null, false), b2))
    }
    val b3 = if (accept(K.K_FINALLY)) block() else null
    AST.TryExpression(p(t), resources, b1, clauses.toList, b3)
  }

  private def expressionElement(): AST.Expression = {
    enterSection()
    val e = assignable()
    eosOrBlockEnd()
    e
  }

  private def elseFollows(): Boolean = {
    val (k, i) = rawKindSkippingEol(0)
    if (k != K.K_ELSE) return false
    var j = i + 1
    while (rawAt(j).kind == K.EOL) j += 1
    val nk = rawAt(j).kind
    nk == K.LBRACE || nk == K.K_IF
  }

  private def ifExpression(): AST.IfExpression = {
    val t = expect(K.K_IF)
    val e = term()
    val b1 = block()
    var b2: AST.BlockExpression = null
    if (elseFollows()) {
      eols()
      expect(K.K_ELSE)
      if (kind(1) == K.LBRACE) b2 = block()
      else {
        enterAssignScope()
        val elif = ifExpression()
        b2 = AST.BlockExpression(elif.location, List(elif), leaveAssignScope())
      }
    }
    AST.IfExpression(p(t), e, b1, b2)
  }

  private def casePattern(): AST.Pattern = {
    var pat = simplePattern()
    if (kind(1) == K.K_WHEN) {
      next()
      val guard = logicalOr()
      pat = AST.GuardedPattern(pat.location, pat, guard)
    }
    pat
  }

  private def simplePattern(): AST.Pattern = {
    val k = kind(1)
    if (k == K.ID && image(1) == "_") { val t = next(); return AST.WildcardPattern(p(t)) }
    if (isId(k) && kind(2) == K.LPAREN) {
      val t = id()
      expect(K.LPAREN)
      val bindings = destructuringBindings()
      expect(K.RPAREN)
      return AST.DestructuringPattern(p(t), c(t), bindings)
    }
    if (isId(k) && kind(2) == K.K_IS) {
      val t = id()
      expect(K.K_IS)
      return AST.TypePattern(p(t), c(t), typ())
    }
    if (isId(k) && kind(2) == K.K_WHEN) {
      val t = id()
      return AST.BindingPattern(p(t), c(t))
    }
    if (k == K.RE_STRING) {
      val t = next()
      val names = new ArrayBuffer[String]()
      if (accept(K.LPAREN)) {
        names += c(id())
        while (accept(K.COMMA)) names += c(id())
        expect(K.RPAREN)
      }
      return AST.RegexPattern(p(t), t.image.substring(3, t.image.length - 1), names.toList)
    }
    AST.ExpressionPattern(term())
  }

  private def destructuringFieldPattern(): AST.Pattern = {
    val k = kind(1)
    if (k == K.ID && image(1) == "_") { val t = next(); return AST.WildcardPattern(p(t)) }
    if (isId(k) && kind(2) == K.LPAREN) {
      val t = id()
      expect(K.LPAREN)
      val bindings = destructuringBindings()
      expect(K.RPAREN)
      return AST.DestructuringPattern(p(t), c(t), bindings)
    }
    if (isId(k) && kind(2) == K.K_IS) {
      val t = id()
      expect(K.K_IS)
      return AST.TypePattern(p(t), c(t), typ())
    }
    val t = expect(K.ID)
    AST.BindingPattern(p(t), c(t))
  }

  private def destructuringBindings(): List[AST.Pattern] = {
    if (!isId(kind(1))) return Nil
    val bs = new ArrayBuffer[AST.Pattern]()
    bs += destructuringFieldPattern()
    while (accept(K.COMMA)) bs += destructuringFieldPattern()
    bs.toList
  }

  private def selectExpression(): AST.SelectExpression = {
    val t1 = expect(K.K_SELECT)
    val e1 = term()
    eols()
    expect(K.LBRACE)
    eols()
    val branches = new ArrayBuffer[(List[AST.Pattern], AST.BlockExpression)]()
    while (la("case")) {
      val t2 = expect(K.K_CASE)
      val patterns = new ArrayBuffer[AST.Pattern]()
      patterns += casePattern()
      while (accept(K.COMMA)) patterns += casePattern()
      expect(K.COLON)
      eols()
      enterAssignScope()
      val ss = blockElements()
      branches += ((patterns.toList, AST.BlockExpression(p(t2), ss, leaveAssignScope())))
      eols()
    }
    eols()
    var elseBlock: AST.BlockExpression = null
    if (la("else")) {
      val t2 = expect(K.K_ELSE)
      expect(K.COLON)
      eols()
      enterAssignScope()
      val ss = if (blockElementFollows()) blockElements() else Nil
      elseBlock = AST.BlockExpression(p(t2), ss, leaveAssignScope())
    }
    eols()
    expect(K.RBRACE)
    AST.SelectExpression(p(t1), e1, branches.toList, elseBlock)
  }

  private def terminatedReturn(): AST.ReturnExpression = {
    enterSection()
    val t = expect(K.K_RETURN)
    val k = kind(1)
    val e = if (k != K.EOL && k != K.EOF && !la(";") && !la("}")) term() else null
    eosOrBlockEnd()
    AST.ReturnExpression(p(t), e)
  }

  private def returnExpression(): AST.ReturnExpression = {
    val t = expect(K.K_RETURN)
    val e = if (isTermStart(kind(1))) term() else null
    AST.ReturnExpression(p(t), e)
  }

  private def synchronizedExpression(): AST.SynchronizedExpression = {
    val t = expect(K.K_SYNCHRONIZED)
    val e = if (!la("{")) term() else null
    AST.SynchronizedExpression(p(t), e, block())
  }

  private def whileExpression(): AST.WhileExpression = {
    val t = expect(K.K_WHILE)
    val e = term()
    AST.WhileExpression(p(t), e, block())
  }

  private def doWhileExpression(): AST.DoWhileExpression = {
    val t = expect(K.K_DO)
    val b = block()
    expect(K.K_WHILE)
    enterSection()
    val e = term()
    eosOrBlockEnd()
    AST.DoWhileExpression(p(t), b, e)
  }

  private def foreachExpression(): AST.ForeachExpression = {
    val t = expect(K.K_FOREACH)
    if (kind(1) == K.LPAREN) {
      next()
      val k = id()
      expect(K.COMMA)
      val v = id()
      expect(K.RPAREN)
      if (la("in")) id()
      val e = term()
      val b = block()
      desugarMapForeach(p(t), c(k), c(v), e, b)
    } else {
      val a = argument()
      if (la("in")) id()
      val e = term()
      val b = block()
      AST.ForeachExpression(p(t), a, e, b)
    }
  }

  private def desugarMapForeach(loc: Location, kName: String, vName: String, mapExpr: AST.Expression, body: AST.BlockExpression): AST.ForeachExpression = {
    val entryName = "__foreach_entry"
    val entryType = AST.TypeNode(loc, AST.ReferenceType("java.util.Map$Entry", true), false)
    val entryArg = AST.Argument(loc, entryName, entryType, null, false)
    val entrySet = new AST.MethodCall(loc, mapExpr, "entrySet", Nil)
    val getKey = new AST.MethodCall(loc, AST.Id(loc, entryName), "getKey", Nil)
    val getValue = new AST.MethodCall(loc, AST.Id(loc, entryName), "getValue", Nil)
    val elems = List[AST.BlockElement](
      AST.LocalVariableDeclaration(loc, AST.M_FINAL, kName, null, getKey),
      AST.LocalVariableDeclaration(loc, AST.M_FINAL, vName, null, getValue),
      body)
    AST.ForeachExpression(loc, entryArg, entrySet, AST.BlockExpression(loc, elems, null))
  }

  private def forInitializer(): AST.ForInitializer = kind(1) match {
    case K.K_VAL | K.K_VAR =>
      localVarDeclaration() match {
        case d: AST.LocalVariableDeclaration => AST.ForInitDeclaration(d)
        case _ => throw fail
      }
    case K.SEMI => AST.ForInitEmpty(p(next()))
    case _ => AST.ForInitExpression(expressionElement())
  }

  private def forExpression(): AST.ForExpression = {
    val t = expect(K.K_FOR)
    val init = forInitializer()
    val e1 = if (kind(1) != K.SEMI) term() else null
    expect(K.SEMI)
    val e2 = if (!la("{")) term() else null
    AST.ForExpression(p(t), init, e1, e2, block())
  }

  // ---------------------------------------------------------------- expressions

  private def expression(): AST.Expression = kind(1) match {
    case K.K_IF => ifExpression()
    case K.K_WHILE => whileExpression()
    case K.K_FOR => forExpression()
    case K.K_SELECT => selectExpression()
    case K.LBRACE => block()
    case K.K_TRY => tryExpression()
    case K.K_THROW => throwExpression()
    case K.K_BREAK => breakExpression()
    case K.K_CONTINUE => continueExpression()
    case K.K_RETURN => returnExpression()
    case K.K_SYNCHRONIZED => synchronizedExpression()
    case K.K_FOREACH => foreachExpression()
    case _ => assignable()
  }

  def term(): AST.Expression = expression()

  private def assignable(): AST.Expression = {
    var a = pipeline()
    val k = kind(1)
    if (isAssignOp(k)) {
      next()
      eols()
      val b = expression()
      noteAssigned(a)
      val loc = span(a, b)
      a = k match {
        case K.ASSIGN => AST.Assignment(loc, a, b)
        case K.ADDEQ => AST.AdditionAssignment(loc, a, b)
        case K.SUBEQ => AST.SubtractionAssignment(loc, a, b)
        case K.MULEQ => AST.MultiplicationAssignment(loc, a, b)
        case K.DIVEQ => AST.DivisionAssignment(loc, a, b)
        case K.MODEQ => AST.ModuloAssignment(loc, a, b)
        case K.ANDEQ => AST.BitAndAssignment(loc, a, b)
        case K.OREQ => AST.BitOrAssignment(loc, a, b)
        case K.XOREQ => AST.XorAssignment(loc, a, b)
        case K.LSHIFTEQ => AST.LeftShiftAssignment(loc, a, b)
        case K.RSHIFTEQ => AST.MathRightShiftAssignment(loc, a, b)
        case K.URSHIFTEQ => AST.LogicalRightShiftAssignment(loc, a, b)
        case _ => throw fail
      }
    }
    a
  }

  private def pipeContinuationFollows(): Boolean = rawKindSkippingEol(0)._1 == K.PIPELINE

  private def pipeline(): AST.Expression = {
    var a = logicalOr()
    while (pipeContinuationFollows()) {
      eols()
      val t = expect(K.PIPELINE)
      eols()
      val b = logicalOr()
      a = pipeInto(p(t), a, b)
    }
    a
  }

  private def pipeInto(loc: Location, value: AST.Expression, rhs: AST.Expression): AST.Expression = rhs match {
    case c: AST.UnqualifiedMethodCall => AST.UnqualifiedMethodCall(loc, c.name, value +: c.args, c.typeArgs)
    case id: AST.Id => new AST.UnqualifiedMethodCall(loc, id.name, List(value))
    case c: AST.MethodCall => AST.MethodCall(loc, c.target, c.name, value +: c.args, c.typeArgs)
    case s: AST.MemberSelection => new AST.MethodCall(loc, s.target, s.name, List(value))
    case c: AST.StaticMethodCall => AST.StaticMethodCall(loc, c.typeRef, c.name, value +: c.args, c.typeArgs)
    case s: AST.StaticMemberSelection => new AST.StaticMethodCall(loc, s.typeRef, s.name, List(value))
    case _ => throw fail
  }

  private def logicalOr(): AST.Expression = {
    var a = logicalAnd()
    var going = true
    while (going) kind(1) match {
      case K.OR =>
        next(); eols()
        val b = logicalAnd()
        a = AST.LogicalOr(span(a, b), a, b)
      case K.ELVIS =>
        next(); eols()
        val b = kind(1) match {
          case K.K_THROW => throwExpression()
          case K.K_RETURN => returnExpression()
          case _ => logicalAnd()
        }
        a = AST.Elvis(span(a, b), a, b)
      case _ => going = false
    }
    a
  }

  private def logicalAnd(): AST.Expression = {
    var a = bitOr()
    while (kind(1) == K.AND) { next(); eols(); val b = bitOr(); a = AST.LogicalAnd(span(a, b), a, b) }
    a
  }

  private def bitOr(): AST.Expression = {
    var a = xor()
    while (kind(1) == K.BAR) { next(); eols(); val b = xor(); a = AST.BitOr(span(a, b), a, b) }
    a
  }

  private def xor(): AST.Expression = {
    var a = bitAnd()
    while (kind(1) == K.EOR) { next(); eols(); val b = bitAnd(); a = AST.XOR(span(a, b), a, b) }
    a
  }

  private def bitAnd(): AST.Expression = {
    var a = equal()
    while (kind(1) == K.AMP) { next(); eols(); val b = equal(); a = AST.BitAnd(span(a, b), a, b) }
    a
  }

  private def equal(): AST.Expression = {
    var a = comparative()
    var going = true
    while (going) kind(1) match {
      case K.REFEQ => next(); eols(); val b = comparative(); a = AST.ReferenceEqual(span(a, b), a, b)
      case K.REFNOTEQ => next(); eols(); val b = comparative(); a = AST.ReferenceNotEqual(span(a, b), a, b)
      case K.EQ => next(); eols(); val b = comparative(); a = AST.Equal(span(a, b), a, b)
      case K.NOTEQ => next(); eols(); val b = comparative(); a = AST.NotEqual(span(a, b), a, b)
      case _ => going = false
    }
    a
  }

  private def comparative(): AST.Expression = {
    var a = rangeExpr()
    var going = true
    while (going) kind(1) match {
      case K.LTEQ => next(); eols(); val b = rangeExpr(); a = AST.LessOrEqual(span(a, b), a, b)
      case K.GTEQ => next(); eols(); val b = rangeExpr(); a = AST.GreaterOrEqual(span(a, b), a, b)
      case K.LT => next(); eols(); val b = rangeExpr(); a = AST.LessThan(span(a, b), a, b)
      case K.GT => next(); eols(); val b = rangeExpr(); a = AST.GreaterThan(span(a, b), a, b)
      case K.K_IS => val t = next(); eols(); val ty = typ(); a = AST.IsInstance(p(t), a, ty)
      case _ => going = false
    }
    a
  }

  private def rangeNew(loc: Location, start: AST.Expression, end: AST.Expression, inclusive: Boolean): AST.Expression =
    AST.NewObject(loc, AST.TypeNode(loc, AST.ReferenceType("onion.Range", true), false),
      List(start, end, AST.BooleanLiteral(loc, inclusive)))

  private def rangeExpr(): AST.Expression = {
    var a = bitShift()
    kind(1) match {
      case K.DOTDOT_LT => val t = next(); eols(); val b = bitShift(); a = rangeNew(p(t), a, b, false)
      case K.DOTDOT => val t = next(); eols(); val b = bitShift(); a = rangeNew(p(t), a, b, true)
      case _ =>
    }
    a
  }

  private def bitShift(): AST.Expression = {
    var e1 = additive()
    var going = true
    while (going) kind(1) match {
      case K.L2S => next(); eols(); val e2 = additive(); e1 = AST.MathLeftShift(span(e1, e2), e1, e2)
      case K.R2S => next(); eols(); val e2 = additive(); e1 = AST.MathRightShift(span(e1, e2), e1, e2)
      case K.R3S => next(); eols(); val e2 = additive(); e1 = AST.LogicalRightShift(span(e1, e2), e1, e2)
      case _ => going = false
    }
    e1
  }

  private def additive(): AST.Expression = {
    var e1 = unaryPrefix()
    var going = true
    while (going) kind(1) match {
      case K.PLUS => next(); eols(); val e2 = unaryPrefix(); e1 = AST.Addition(span(e1, e2), e1, e2)
      case K.MINUS => next(); eols(); val e2 = unaryPrefix(); e1 = AST.Subtraction(span(e1, e2), e1, e2)
      case _ => going = false
    }
    e1
  }

  private def unaryPrefix(): AST.Expression = kind(1) match {
    case K.PLUS => val t = next(); AST.Posit(p(t), unaryPrefix())
    case K.MINUS => val t = next(); AST.Negate(p(t), unaryPrefix())
    case K.NOT => val t = next(); AST.Not(p(t), unaryPrefix())
    case K.BN => val t = next(); AST.BitNot(p(t), unaryPrefix())
    case _ => multitive()
  }

  private def multitive(): AST.Expression = {
    var e1 = primarySuffix()
    var going = true
    while (going) kind(1) match {
      case K.STAR => next(); eols(); val e2 = mulOperand(); e1 = AST.Multiplication(span(e1, e2), e1, e2)
      case K.SLASH => next(); eols(); val e2 = mulOperand(); e1 = AST.Division(span(e1, e2), e1, e2)
      case K.PERC => next(); eols(); val e2 = mulOperand(); e1 = AST.Modulo(span(e1, e2), e1, e2)
      case _ => going = false
    }
    e1
  }

  private def mulOperand(): AST.Expression = kind(1) match {
    case K.PLUS => val t = next(); AST.Posit(p(t), mulOperand())
    case K.MINUS => val t = next(); AST.Negate(p(t), mulOperand())
    case K.NOT => val t = next(); AST.Not(p(t), mulOperand())
    case K.BN => val t = next(); AST.BitNot(p(t), mulOperand())
    case _ => primarySuffix()
  }

  private def dotContinuationFollows(): Boolean = {
    val k = rawKindSkippingEol(0)._1
    k == K.DOT || k == K.SAFE_ACCESS
  }

  private def suffixContinues(): Boolean = {
    val k = kind(1)
    if (k == K.SAFE_INDEX || k == K.DOT || k == K.SAFE_ACCESS || k == K.K_AS || k == K.NOT_NULL) return true
    if (k == K.LBRACKET || k == K.PLUSPLUS || k == K.MINUSMINUS) return true
    dotContinuationFollows()
  }

  private def primarySuffix(): AST.Expression = {
    var e = primary()
    while (suffixContinues()) {
      eols()
      kind(1) match {
        case K.LBRACKET =>
          val t = next(); eols(); val a = term(); eols(); expect(K.RBRACKET)
          e = AST.Indexing(p(t), e, a)
        case K.SAFE_INDEX =>
          val t = next(); eols(); val a = term(); eols(); expect(K.RBRACKET)
          e = AST.SafeIndexing(p(t), e, a)
        case K.DOT => e = dotSuffix(e)
        case K.SAFE_ACCESS => e = safeAccessSuffix(e)
        case K.K_AS =>
          val t = next(); eols(); val ty = typ()
          e = AST.Cast(p(t), e, ty)
        case K.PLUSPLUS =>
          val t = next(); noteAssigned(e); e = AST.PostIncrement(p(t), e)
        case K.NOT_NULL =>
          val t = next(); e = AST.NotNullAssertion(p(t), e)
        case K.MINUSMINUS =>
          val t = next(); noteAssigned(e); e = AST.PostDecrement(p(t), e)
        case _ => throw fail
      }
    }
    e
  }

  private def trailingLambdaOpt(): AST.ClosureExpression =
    if (kind(1) == K.LBRACE && looksLike(trailingLambdaHead())) trailingLambda() else null

  private def typeArgsThenParen(): Boolean =
    kind(1) == K.LBRACKET && looksLike { typeArguments(); eols(); expect(K.LPAREN) }

  private def dotSuffix(e: AST.Expression): AST.Expression = {
    val t = expect(K.DOT)
    eols()
    val n = id()
    if (typeArgsThenParen()) {
      val tas = typeArguments()
      eols()
      expect(K.LPAREN)
      eols()
      var args = terms()
      expect(K.RPAREN)
      val tl = trailingLambdaOpt()
      args = AST.appendToList(args, tl)
      AST.MethodCall(through(n), e, c(n), args, tas)
    } else if (kind(1) == K.LPAREN) {
      next()
      eols()
      var args = terms()
      expect(K.RPAREN)
      val tl = trailingLambdaOpt()
      args = AST.appendToList(args, tl)
      new AST.MethodCall(through(n), e, c(n), args)
    } else {
      val tl = trailingLambdaOpt()
      if (tl != null) new AST.MethodCall(through(n), e, c(n), List(tl))
      else AST.MemberSelection(p(t), e, c(n))
    }
  }

  private def safeAccessSuffix(e: AST.Expression): AST.Expression = {
    val t = expect(K.SAFE_ACCESS)
    eols()
    val n = id()
    if (typeArgsThenParen()) {
      val tas = typeArguments()
      eols()
      expect(K.LPAREN)
      eols()
      var args = terms()
      expect(K.RPAREN)
      val tl = trailingLambdaOpt()
      args = AST.appendToList(args, tl)
      AST.SafeMethodCall(p(t), e, c(n), args, tas)
    } else if (kind(1) == K.LPAREN) {
      next()
      eols()
      var args = terms()
      expect(K.RPAREN)
      val tl = trailingLambdaOpt()
      args = AST.appendToList(args, tl)
      new AST.SafeMethodCall(p(t), e, c(n), args)
    } else {
      val tl = trailingLambdaOpt()
      if (tl != null) new AST.SafeMethodCall(p(t), e, c(n), List(tl))
      else AST.SafeMemberSelection(p(t), e, c(n))
    }
  }

  private def staticAccessAhead(): Boolean = {
    var k = 1
    val k1 = kind(1)
    if (k1 == K.ID) {
      while (kind(k + 1) == K.DOT && kind(k + 2) == K.ID) k += 2
    } else if (k1 == K.FQCN || isBasicKind(k1)) {
      // a single-token receiver
    } else return false
    if (kind(k + 1) == K.LBRACKET) {
      var depth = 0
      k += 1
      var going = true
      while (going && k <= 4096) {
        val t = kind(k)
        if (t == K.LBRACKET) depth += 1
        else if (t == K.RBRACKET) { depth -= 1; if (depth == 0) going = false }
        else if (t == K.EOF) return false
        if (going) k += 1
      }
      if (depth != 0) return false
    }
    kind(k + 1) == K.COLON2
  }

  private def arrowAfterParenAhead(): Boolean = {
    if (kind(1) != K.LPAREN) return false
    var depth = 0
    var k = 1
    var going = true
    while (going && k <= 4096) {
      val kk = kind(k)
      if (kk == K.LPAREN) depth += 1
      else if (kk == K.RPAREN) { depth -= 1; if (depth == 0) going = false }
      else if (kk == K.EOF) return false
      if (going) k += 1
    }
    if (depth != 0) return false
    k += 1
    while (kind(k) == K.EOL) k += 1
    kind(k) == K.ARROW
  }

  /** The `Type::member` alternatives, in the grammar's order; falls through to `primaryRest`. */
  private def staticAccessPrimary(): AST.Expression = {
    if (looksLike { classType(); typeArguments(); expect(K.COLON2) }) {
      val ty = classType()
      val tas = typeArguments()
      expect(K.COLON2)
      val n = id()
      expect(K.LPAREN)
      var es = terms()
      expect(K.RPAREN)
      es = AST.appendToList(es, trailingLambdaOpt())
      needsBodyRewrite = true
      return AST.TraitMethodCall(through(n), ty, tas, c(n), es)
    }
    if (isBasicKind(kind(1))) {
      if (looksLike { boxedClassType(); expect(K.COLON2); id(); typeArguments(); expect(K.LPAREN) }) {
        val ty = boxedClassType(); expect(K.COLON2); val n = id(); val tas = typeArguments()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return AST.StaticMethodCall(through(n), ty, c(n), es, tas)
      }
      if (looksLike { boxedClassType(); expect(K.COLON2); id(); expect(K.LPAREN) }) {
        val ty = boxedClassType(); expect(K.COLON2); val n = id()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return new AST.StaticMethodCall(through(n), ty, c(n), es)
      }
      if (looksLike { boxedClassType(); expect(K.COLON2) }) {
        val ty = boxedClassType(); val t = expect(K.COLON2); val n = id()
        return AST.StaticMemberSelection(p(t), ty, c(n))
      }
    }
    if (isId(kind(1)) && kind(2) == K.DOT) {
      if (looksLike { dottedClassType(); expect(K.COLON2); id(); typeArguments(); expect(K.LPAREN) }) {
        val ty = dottedClassType(); expect(K.COLON2); val n = id(); val tas = typeArguments()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return AST.StaticMethodCall(through(n), ty, c(n), es, tas)
      }
      if (looksLike { dottedClassType(); expect(K.COLON2); id(); expect(K.LPAREN) }) {
        val ty = dottedClassType(); expect(K.COLON2); val n = id()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return new AST.StaticMethodCall(through(n), ty, c(n), es)
      }
      if (looksLike { dottedClassType(); expect(K.COLON2) }) {
        val ty = dottedClassType(); val t = expect(K.COLON2); val n = id()
        return AST.StaticMemberSelection(p(t), ty, c(n))
      }
    }
    if (looksLike { classType(); expect(K.COLON2); id(); typeArguments(); expect(K.LPAREN) }) {
      val ty = classType(); expect(K.COLON2); val n = id(); val tas = typeArguments()
      expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
      es = AST.appendToList(es, trailingLambdaOpt())
      return AST.StaticMethodCall(through(n), ty, c(n), es, tas)
    }
    if ((isId(kind(1)) || kind(1) == K.FQCN) && kind(2) == K.COLON2 && isId(kind(3)) && kind(4) == K.LPAREN) {
      val ty = classType(); expect(K.COLON2); val n = id()
      expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
      es = AST.appendToList(es, trailingLambdaOpt())
      return new AST.StaticMethodCall(through(n), ty, c(n), es)
    }
    if ((isId(kind(1)) || kind(1) == K.FQCN) && kind(2) == K.COLON2) {
      val ty = classType(); val t = expect(K.COLON2); val n = id()
      return AST.StaticMemberSelection(p(t), ty, c(n))
    }
    primaryRest()
  }

  private def lambdaOrParen(): AST.Expression = {
    if (looksLike(lambdaHead())) arrowAnonymousFunction()
    else {
      expect(K.LPAREN); eols()
      val e = term()
      eols(); expect(K.RPAREN)
      e
    }
  }

  private def primary(): AST.Expression = {
    if (kind(1) == K.K_SUPER) {
      if (looksLike { expect(K.K_SUPER); eols(); expect(K.DOT); eols(); id(); typeArguments(); expect(K.LPAREN) }) {
        val t = next(); eols(); expect(K.DOT); eols()
        val n = id()
        val tas = typeArguments()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return AST.SuperMethodCall(p(t), c(n), es, tas)
      }
      next(); eols(); expect(K.DOT); eols()
      val n = id()
      var es: List[AST.Expression] = Nil
      if (kind(1) == K.LPAREN) { next(); es = terms(); expect(K.RPAREN) }
      es = AST.appendToList(es, trailingLambdaOpt())
      return new AST.SuperMethodCall(through(n), c(n), es)
    }
    if (staticAccessAhead()) staticAccessPrimary() else primaryRest()
  }

  private def primaryRest(): AST.Expression = {
    val k = kind(1)
    if (isId(k)) {
      if (kind(2) == K.LBRACKET && looksLike { id(); typeArguments(); expect(K.LPAREN) }) {
        val t = id()
        val tas = typeArguments()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return AST.UnqualifiedMethodCall(through(t), c(t), es, tas)
      }
      if (kind(2) == K.LPAREN) {
        val t = id()
        expect(K.LPAREN); var es = terms(); expect(K.RPAREN)
        es = AST.appendToList(es, trailingLambdaOpt())
        return new AST.UnqualifiedMethodCall(through(t), c(t), es)
      }
      if (kind(2) == K.ARROW) return arrowAnonymousFunction()
      val t = id()
      return AST.Id(p(t), c(t))
    }
    k match {
      case K.LBRACKET =>
        enterDefault()
        val t = next()
        eols()
        val e = bracketLiteralRest(t)
        leaveDefault()
        e
      case K.LPAREN =>
        if (arrowAfterParenAhead()) lambdaOrParen()
        else {
          next(); eols()
          val e = term()
          eols(); expect(K.RPAREN)
          e
        }
      case K.K_DO if kind(2) == K.LBRACKET => doExpression()
      case K.K_NEW =>
        if (looksLike { expect(K.K_NEW); rawType(); expect(K.LBRACKET); expect(K.RBRACKET); expect(K.LBRACE) }) {
          val t = next()
          val ty = rawType()
          expect(K.LBRACKET); expect(K.RBRACKET); expect(K.LBRACE)
          eols()
          val es = terms()
          expect(K.RBRACE)
          AST.NewArrayWithValues(p(t), ty, es)
        } else newExpression()
      case K.K_SELF => AST.CurrentInstance(p(next()))
      case K.K_THIS => AST.CurrentInstance(p(next()))
      case K.INTEGER => integerLiteral()
      case K.FLOAT => floatLiteral()
      case K.CHARACTER =>
        val t = next()
        AST.CharacterLiteral(p(t), unescape(t.image.substring(1, t.image.length - 1)).charAt(0))
      case K.STRING | K.MULTI_LINE_STRING => stringLiteral()
      case K.RE_STRING => schemeCall(next(), 2)
      case K.FILE_STRING => schemeCall(next(), 4)
      case K.HTTP_STRING => schemeCall(next(), 4)
      case K.SCHEME_STRING => val t = next(); schemeCall(t, t.image.indexOf('"'))
      case K.K_TRUE => AST.BooleanLiteral(p(next()), true)
      case K.K_FALSE => AST.BooleanLiteral(p(next()), false)
      case K.K_NULL => AST.NullLiteral(p(next()))
      case _ => throw fail
    }
  }

  private def newBracketsAreTypeArgs(): Boolean = {
    if (last != null && isBasicKind(last.kind)) return false
    var depth = 0
    var i = 1
    var firstKind = -1
    var firstImage: String = null
    var topLevelTokens = 0
    var sawTypeMarker = false
    var sawValueMarker = false
    var sawNestedBracket = false
    var closeIndex = -1
    var going = true
    while (going) {
      val t = peek(i)
      val k = t.kind
      if (k == K.EOF) going = false
      else if (k == K.LBRACKET) { depth += 1; if (depth > 1) sawNestedBracket = true; i += 1 }
      else if (k == K.RBRACKET) { depth -= 1; if (depth == 0) { closeIndex = i; i += 1; going = false } else i += 1 }
      else {
        if (depth == 1 && k != K.EOL) {
          if (firstKind == -1) { firstKind = k; firstImage = t.image }
          topLevelTokens += 1
          if (k == K.COMMA || k == K.QUESTION || k == K.K_EXTENDS || k == K.K_SUPER || k == K.FQCN || k == K.SAFE_INDEX) sawTypeMarker = true
          else if (k == K.DOT || k == K.LPAREN) sawValueMarker = true
        }
        i += 1
      }
    }
    if (closeIndex < 0) return true
    var j = closeIndex + 1
    while (kind(j) == K.EOL) j += 1
    if (kind(j) == K.LPAREN) return true
    if (sawValueMarker) return false
    if (sawNestedBracket || sawTypeMarker) return true
    if (topLevelTokens == 1 && firstKind != -1) {
      if (firstKind == K.INTEGER) return false
      if (isBasicKind(firstKind)) return true
      if (firstKind == K.ID) {
        if (firstImage == null || firstImage.isEmpty) return false
        return Character.isUpperCase(firstImage.charAt(0))
      }
      return false
    }
    false
  }

  private def newExpression(): AST.Expression = {
    val t = expect(K.K_NEW)
    var ty = rawType()
    if (kind(1) == K.LBRACKET && newBracketsAreTypeArgs()) {
      next()
      eols()
      val newTas = new ArrayBuffer[AST.TypeDescriptor]()
      newTas += typ().desc
      while (accept(K.COMMA)) { eols(); newTas += typ().desc }
      eols()
      expect(K.RBRACKET)
      ty = AST.TypeNode(ty.location, AST.ParameterizedType(ty.desc, newTas.toList), ty.isRelaxed)
    }
    def moreSizes(sizes: ArrayBuffer[AST.Expression]): Unit =
      while (kind(1) == K.LBRACKET && kind(2) != K.RBRACKET) {
        next(); eols(); sizes += term(); expect(K.RBRACKET)
      }
    if (kind(1) == K.LBRACKET && kind(2) != K.RBRACKET) {
      val sizes = new ArrayBuffer[AST.Expression]()
      moreSizes(sizes)
      AST.NewArray(p(t), ty, sizes.toList)
    } else if (kind(1) == K.SAFE_INDEX && kind(2) != K.RBRACKET) {
      next(); eols()
      val sizes = new ArrayBuffer[AST.Expression]()
      sizes += term()
      expect(K.RBRACKET)
      moreSizes(sizes)
      val nty = AST.TypeNode(ty.location, AST.NullableType(ty.desc), ty.isRelaxed)
      AST.NewArray(p(t), nty, sizes.toList)
    } else {
      var es: List[AST.Expression] = Nil
      if (kind(1) == K.LPAREN) { next(); eols(); es = terms(); expect(K.RPAREN) }
      AST.NewObject(p(t), ty, es)
    }
  }

  private def argumentExpr(): AST.Expression = {
    if (isId(kind(1)) && kind(2) == K.ASSIGN) {
      val n = id()
      expect(K.ASSIGN)
      eols()
      val e = term()
      AST.NamedArgument(p(n), c(n), e)
    } else term()
  }

  private def terms(): List[AST.Expression] = {
    eols()
    if (kind(1) == K.RPAREN) return Nil
    val args = new ArrayBuffer[AST.Expression]()
    args += argumentExpr()
    while (accept(K.COMMA)) { eols(); args += argumentExpr() }
    eols()
    args.toList
  }

  private def bracketLiteralRest(t: Token): AST.Expression = {
    if (kind(1) == K.COLON) { next(); eols(); expect(K.RBRACKET); return AST.MapLiteral(p(t), Nil) }
    if (kind(1) == K.RBRACKET) { next(); return AST.ListLiteral(p(t), Nil) }
    val first = argumentExpr()
    if (kind(1) == K.COLON) {
      next(); eols()
      val entries = new ArrayBuffer[(AST.Expression, AST.Expression)]()
      entries += ((first, term()))
      while (accept(K.COMMA)) {
        eols()
        val k = term()
        expect(K.COLON)
        eols()
        entries += ((k, term()))
      }
      eols()
      expect(K.RBRACKET)
      AST.MapLiteral(p(t), entries.toList)
    } else {
      val elems = new ArrayBuffer[AST.Expression]()
      elems += first
      while (accept(K.COMMA)) { eols(); elems += argumentExpr() }
      eols()
      expect(K.RBRACKET)
      AST.ListLiteral(p(t), elems.toList)
    }
  }

  private def functionTypeNode(t: Token, arity: Int): AST.TypeNode =
    AST.TypeNode(p(t), AST.ReferenceType("onion.Function" + arity, true), true)

  private def arrowAnonymousFunction(): AST.ClosureExpression = {
    enterDefault()
    var t: Token = null
    var args: List[AST.Argument] = Nil
    if (kind(1) == K.LPAREN) {
      t = next()
      args = lambdaArgs(true)
      expect(K.RPAREN)
    } else {
      t = id()
      args = List(AST.Argument(p(t), c(t), null, null, false))
    }
    eols()
    expect(K.ARROW)
    eols()
    if (kind(1) == K.LBRACE) {
      val body = block()
      val ty = functionTypeNode(t, args.length)
      leaveDefault()
      AST.ClosureExpression(p(t), ty, "call", args, null, body)
    } else {
      leaveDefault()
      enterAssignScope()
      val eb = term()
      val body = AST.BlockExpression(eb.location, List(eb), leaveAssignScope())
      AST.ClosureExpression(p(t), functionTypeNode(t, args.length), "call", args, null, body)
    }
  }

  private def doExpression(): AST.DoExpression = {
    enterDefault()
    val t = expect(K.K_DO)
    expect(K.LBRACKET)
    val monadType = typ()
    expect(K.RBRACKET)
    expect(K.LBRACE)
    eols()
    val stmts = doClauses()
    eols()
    expect(K.RBRACE)
    leaveDefault()
    needsBodyRewrite = true
    AST.DoExpression(p(t), monadType, stmts)
  }

  private def doClauses(): List[Any] = {
    val stmts = new ArrayBuffer[Any]()
    stmts += doClause()
    var going = true
    while (going) {
      if (kind(1) == K.SEMI) next()
      else if (kind(1) == K.RBRACE || kind(1) == K.EOF) going = false
      else stmts += doClause()
    }
    stmts.toList
  }

  private def doClause(): Any = {
    if (kind(1) == K.K_RET) {
      val t = next(); eols()
      return AST.RetStatement(p(t), term())
    }
    if (isId(kind(1)) && looksLike { id(); eols(); expect(K.LARROW) }) {
      val t = id(); eols(); expect(K.LARROW); eols()
      return AST.DoBinding(p(t), c(t), term())
    }
    term()
  }

  private def trailingLambda(): AST.ClosureExpression = {
    enterDefault()
    val t = expect(K.LBRACE)
    eols()
    val args = lambdaArgs(false)
    eols()
    expect(K.ARROW)
    eols()
    enterAssignScope()
    val stmts = if (kind(1) != K.RBRACE) blockElements() else Nil
    eols()
    expect(K.RBRACE)
    val body = AST.BlockExpression(p(t), stmts, leaveAssignScope())
    val ty = functionTypeNode(t, args.length)
    leaveDefault()
    AST.ClosureExpression(p(t), ty, "call", args, null, body)
  }

  // ---------------------------------------------------------------- literals

  private def integerLiteral(): AST.Expression = {
    val t = expect(K.INTEGER)
    val s = t.image.replace("_", "")
    if (s.endsWith("B")) {
      val iv = parseIntLiteral(s.substring(0, s.length - 1))
      if (iv < -128 || iv > 255) throw fail
      AST.ByteLiteral(p(t), iv.toByte)
    } else if (s.endsWith("S")) {
      val iv = parseIntLiteral(s.substring(0, s.length - 1))
      if (iv < -32768 || iv > 65535) throw fail
      AST.ShortLiteral(p(t), iv.toShort)
    } else if (s.endsWith("L")) AST.LongLiteral(p(t), parseLongLiteral(s.substring(0, s.length - 1)))
    else AST.IntegerLiteral(p(t), parseIntLiteral(s))
  }

  private def floatLiteral(): AST.Expression = {
    val t = expect(K.FLOAT)
    val s = t.image.replace("_", "")
    try {
      if (s.endsWith("F") || s.endsWith("f")) AST.FloatLiteral(p(t), java.lang.Float.parseFloat(s.substring(0, s.length - 1)))
      else if (s.endsWith("D") || s.endsWith("d")) AST.DoubleLiteral(p(t), java.lang.Double.parseDouble(s.substring(0, s.length - 1)))
      else AST.DoubleLiteral(p(t), java.lang.Double.parseDouble(s))
    } catch { case _: NumberFormatException => throw fail }
  }

  private def schemeCall(t: Token, prefixLen: Int): AST.Expression = {
    val name = t.image.substring(0, prefixLen)
    val raw = t.image.substring(prefixLen + 1, t.image.length - 1)
    new AST.UnqualifiedMethodCall(p(t), name, List(AST.StringLiteral(p(t), raw)))
  }

  private def stringLiteral(): AST.Expression = {
    val t = next()
    val str = t.image
    if (t.kind == K.STRING) {
      if (str.contains("#{") && findInterpStart(str.substring(1, str.length - 1), 0) != -1) interpolated(p(t), str, 1)
      else AST.StringLiteral(p(t), unescape(str.substring(1, str.length - 1)))
    } else {
      if (str.contains("#{")) interpolated(p(t), str, 3)
      else AST.StringLiteral(p(t), str.substring(3, str.length - 3))
    }
  }

  /** An ASCII identifier that is not a keyword: what the lexer would turn into a single ID token. */
  private def isPlainIdentifier(text: String): Boolean = {
    val n = text.length
    if (n == 0 || n >= 32) return false
    val c0 = text.charAt(0)
    if (!((c0 >= 'a' && c0 <= 'z') || (c0 >= 'A' && c0 <= 'Z') || c0 == '_')) return false
    var i = 1
    while (i < n) {
      val c = text.charAt(i)
      if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_')) return false
      i += 1
    }
    OnionLexer.keywordKind(text.toCharArray, 0, n) == -1
  }

  /** `parseInterpolatedString` / `parseMultiLineInterpolatedString` of the grammar. */
  private def interpolated(loc: Location, whole: String, quoteLen: Int): AST.Expression = {
    val str = whole.substring(quoteLen, whole.length - quoteLen)
    val single = quoteLen == 1
    val parts = new ArrayBuffer[String]()
    val expressions = new ArrayBuffer[AST.Expression]()
    var start = 0
    var pos = 0
    var going = true
    while (going && pos < str.length) {
      val interpStart = if (single) findInterpStart(str, pos) else str.indexOf("#{", pos)
      if (interpStart == -1) {
        parts += (if (single) unescape(str.substring(start)) else str.substring(start))
        going = false
      } else {
        parts += (if (single) unescape(str.substring(start, interpStart)) else str.substring(start, interpStart))
        var braceCount = 1
        var interpEnd = interpStart + 2
        while (interpEnd < str.length && braceCount > 0) {
          val ch = str.charAt(interpEnd)
          if (ch == '"') {
            interpEnd += 1
            while (interpEnd < str.length && str.charAt(interpEnd) != '"') {
              if (str.charAt(interpEnd) == '\\') interpEnd += 1
              interpEnd += 1
            }
          } else if (ch == '{') braceCount += 1
          else if (ch == '}') braceCount -= 1
          interpEnd += 1
        }
        if (braceCount > 0) throw fail
        val exprStr = str.substring(interpStart + 2, interpEnd - 1)
        val (line, col) = interpolationOrigin(str, interpStart + 2, loc, quoteLen)
        // `#{name}` is most interpolations; a plain identifier needs no sub-parser (and its
        // lexer). Anything else is parsed as a term at its position in the enclosing file.
        val expr =
          if (isPlainIdentifier(exprStr)) AST.Id(Location(line, col, line, col + exprStr.length - 1), exprStr)
          else {
            val sub = new OnionParser(exprStr, line - 1, col - 1)
            val parsed = try sub.term() catch { case _: Exception => throw fail }
            needsBodyRewrite |= sub.needsBodyRewrite
            parsed
          }
        expressions += expr
        start = interpEnd
        pos = interpEnd
      }
    }
    if (parts.length == expressions.length) parts += ""
    AST.StringInterpolation(loc, parts.toList, expressions.toList)
  }
}
