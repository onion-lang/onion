package onion.tools.lsp

import java.io.StringReader

import onion.compiler.parser.{JJOnionParserConstants as K, JJOnionParserTokenManager, JavaCharStream, Token}

import scala.collection.mutable
import scala.jdk.CollectionConverters.*

/**
 * Semantic tokens: the colouring a TextMate grammar cannot produce.
 *
 * The shipped grammar decides what a word is by the shape of the line around it, so every
 * identifier looks alike — a class, a method, a field and a local are all just words. This
 * classifies each one by what the document actually declares it to be, which is the whole
 * reason a language server offers colouring at all.
 *
 * '''It emits nothing it is unsure of.''' An identifier the document does not declare
 * produces no token, and the editor falls back to the TextMate grammar for it. Emitting a
 * guess would be worse than silence: semantic tokens override the grammar, so a wrong guess
 * replaces a right answer.
 *
 * Two encoding details the protocol makes easy to get wrong:
 *
 *   - Tokens are '''delta-encoded''' — five integers each, relative to the token before —
 *     so they must be emitted in source order or the whole file's colouring shifts.
 *   - A token '''cannot span lines'''. A block comment or a multi-line string is therefore
 *     split into one token per line, which is why the lexer's own extents are not used
 *     directly.
 *
 * Keyword and operator kinds are derived from the generated parser's own `tokenImage`
 * table rather than listed here, so a keyword added to the grammar is classified without
 * anyone remembering to update this file. That is the same duplication that rotted the
 * TextMate grammar to 70% coverage; here it is simply absent.
 */
object OnionSemanticTokens:

  /** The legend, in index order — the protocol identifies a type by its position here. */
  val TokenTypes: java.util.List[String] = List(
    "keyword", "comment", "string", "number", "regexp", "operator",
    "class", "interface", "enum", "struct", "method", "property", "variable",
    "type", "function").asJava

  val TokenModifiers: java.util.List[String] = List("declaration").asJava

  private val Keyword = 0
  private val Comment = 1
  private val StringType = 2
  private val Number = 3
  private val Regexp = 4
  private val Operator = 5
  private val ClassType = 6
  private val InterfaceType = 7
  private val EnumType = 8
  private val StructType = 9
  private val MethodType = 10
  private val PropertyType = 11
  private val VariableType = 12
  private val BuiltinType = 13
  private val FunctionType = 14

  /**
   * Kinds whose fixed image is a word — the keywords, derived from the parser rather than
   * duplicated. `tokenImage` holds each fixed token quoted, so `"class"` is a keyword and
   * `"<="` is not.
   */
  private lazy val KeywordKinds: Set[Int] =
    fixedImages.collect {
      case (kind, image) if image.forall(_.isLetter) && !image.head.isUpper => kind
    }.toSet

  /**
   * Keywords that name a type. Onion writes its primitives capitalised -- `Int`, `Boolean`,
   * `Char` -- and nothing else in the grammar's fixed images starts with a capital, so the
   * split is read off the parser's table rather than listed.
   */
  private lazy val TypeKeywordKinds: Set[Int] =
    fixedImages.collect {
      case (kind, image) if image.forall(_.isLetter) && image.head.isUpper => kind
    }.toSet

  /**
   * Kinds whose fixed image is built from operator characters. Brackets, commas and
   * semicolons are deliberately excluded: no theme colours them, and marking them would
   * override the grammar with something no more informative.
   */
  private lazy val OperatorKinds: Set[Int] =
    fixedImages.collect {
      case (kind, image) if image.nonEmpty && image.forall("+-*/%<>=!&|^~?:".contains(_)) => kind
    }.toSet

  private def fixedImages: Seq[(Int, String)] =
    K.tokenImage.toSeq.zipWithIndex.collect {
      case (image, kind) if image.length > 2 && image.startsWith("\"") && image.endsWith("\"") =>
        (kind, image.substring(1, image.length - 1))
    }

  /** Types the language provides, which no document declares and every document uses. */
  private val BuiltinTypes = Set(
    "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char", "String",
    "Object", "Void", "List", "Map", "Set")

  /** One token, before delta encoding. */
  private final case class Emitted(line: Int, start: Int, length: Int, tokenType: Int, declaration: Boolean)

  /**
   * @param classify what the document declares an identifier to be, or `None` to leave it
   *                 to the TextMate grammar
   * @param isDeclarationAt whether the identifier at this position is its own declaration
   */
  def encode(
    content: String,
    classify: String => Option[org.eclipse.lsp4j.SymbolKind],
    isDeclarationAt: (String, Int, Int) => Boolean
  ): java.util.List[Integer] =
    val lines = content.split("\n", -1)
    val emitted = mutable.Buffer[Emitted]()

    tokens(content).foreach { case (token, isComment) =>
      val tokenType =
        if isComment then Some(Comment)
        else classifyToken(token, classify)
      tokenType.foreach { kind =>
        val declaration =
          kind != Comment && token.kind == K.ID &&
            isDeclarationAt(token.image, token.beginLine - 1, token.beginColumn)
        emitted ++= split(token, kind, declaration, lines)
      }
    }

    delta(emitted.sortBy(t => (t.line, t.start)).toSeq)

  private def classifyToken(
    token: Token,
    classify: String => Option[org.eclipse.lsp4j.SymbolKind]
  ): Option[Int] =
    import org.eclipse.lsp4j.SymbolKind as S
    token.kind match
      case K.STRING | K.CHARACTER | K.INTERP_STRING | K.MULTI_LINE_STRING => Some(StringType)
      case K.RE_STRING => Some(Regexp)
      case K.FILE_STRING | K.HTTP_STRING | K.SCHEME_STRING => Some(StringType)
      case K.INTEGER | K.FLOAT => Some(Number)
      case K.QUOTED_ID => Some(VariableType)
      case K.ID =>
        classify(token.image).flatMap {
          case S.Class => Some(ClassType)
          case S.Interface => Some(InterfaceType)
          case S.Enum => Some(EnumType)
          case S.Struct => Some(StructType)
          case S.Method | S.Constructor => Some(MethodType)
          case S.Field | S.Property => Some(PropertyType)
          case S.Variable => Some(VariableType)
          case S.Function => Some(FunctionType)
          case _ => None
        }.orElse(Option.when(BuiltinTypes.contains(token.image))(BuiltinType))
      // A primitive type is a keyword to the lexer and a type to a reader. Onion capitalises
      // exactly these -- `Int`, `Boolean`, `Char` -- so the distinction comes from the
      // parser's own table rather than from a list kept here.
      case kind if TypeKeywordKinds.contains(kind) => Some(BuiltinType)
      case kind if KeywordKinds.contains(kind) => Some(Keyword)
      case kind if OperatorKinds.contains(kind) => Some(Operator)
      case _ => None

  /**
   * A token as one entry per line it occupies. The protocol has no way to express a token
   * that spans a line break, so a block comment has to arrive as several.
   */
  private def split(
    token: Token,
    tokenType: Int,
    declaration: Boolean,
    lines: Array[String]
  ): Seq[Emitted] =
    val segments = token.image.split("\n", -1)
    segments.zipWithIndex.flatMap { (segment, offset) =>
      val line = token.beginLine - 1 + offset
      if line < 0 || line >= lines.length || segment.isEmpty then None
      else
        // Only the first line starts where the token does; a continuation starts at the
        // column the text itself begins on, which is where the segment sits in that line.
        val start =
          if offset == 0 then charColumn(lines(line), token.beginColumn)
          else math.max(0, lines(line).indexOf(segment))
        Some(Emitted(line, start, segment.length, tokenType, declaration))
    }.toSeq

  private def delta(sorted: Seq[Emitted]): java.util.List[Integer] =
    val out = new java.util.ArrayList[Integer](sorted.size * 5)
    var previousLine = 0
    var previousStart = 0
    sorted.foreach { token =>
      val deltaLine = token.line - previousLine
      val deltaStart = if deltaLine == 0 then token.start - previousStart else token.start
      out.add(deltaLine)
      out.add(deltaStart)
      out.add(token.length)
      out.add(token.tokenType)
      out.add(if token.declaration then 1 else 0)
      previousLine = token.line
      previousStart = token.start
    }
    out

  /**
   * Maps JavaCC's 1-based, tab-expanded column to a 0-based character index. A tab advances
   * the column to the next multiple of eight, so on a tab-indented line the two differ and
   * every token on it lands in the wrong place.
   */
  private def charColumn(lineText: String, expandedColumn: Int): Int =
    val target = math.max(0, expandedColumn - 1)
    var expanded = 0
    var index = 0
    while index < lineText.length && expanded < target do
      expanded = if lineText.charAt(index) == '\t' then (expanded / 8 + 1) * 8 else expanded + 1
      index += 1
    index

  /** Every token and comment, in source order. Comments arrive attached to the token after. */
  private def tokens(content: String): Seq[(Token, Boolean)] =
    val out = mutable.Buffer[(Token, Boolean)]()
    try
      val manager = new JJOnionParserTokenManager(new JavaCharStream(new StringReader(content)))
      var token = manager.getNextToken()
      var done = false
      while !done do
        val attached = mutable.Buffer[Token]()
        var special = token.specialToken
        while special != null do
          attached.prepend(special)
          special = special.specialToken
        attached.foreach(comment => out += ((comment, true)))
        if token.kind == K.EOF then done = true
        else
          out += ((token, false))
          token = manager.getNextToken()
    catch
      // A half-typed file is the normal case in an editor: an unterminated string is a
      // lexer error, and colouring what was read is better than colouring nothing.
      case _: Throwable => ()
    out.toSeq
