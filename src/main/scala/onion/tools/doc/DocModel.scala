package onion.tools.doc

import java.io.StringReader
import onion.compiler.AST
import onion.compiler.parser.JJOnionParser

/** A documented member (method, field, or constructor) within a type. */
final case class DocMember(
  kind: String,        // "method" | "field" | "constructor"
  name: String,
  signature: String,
  line: Int,
  doc: Option[DocComment]
)

/** A documented top-level type (class, interface, or record). */
final case class DocType(
  kind: String,        // "class" | "interface" | "record"
  name: String,
  signature: String,
  line: Int,
  doc: Option[DocComment],
  constructors: List[DocMember],
  methods: List[DocMember],
  fields: List[DocMember]
)

/** A whole documented compilation unit (one source file). */
final case class DocFile(sourceName: String, types: List[DocType])

object DocModel {

  /**
   * Build a documented model from Onion source text.
   *
   * @param sourceText the raw `.on` source
   * @param sourceName a display name for the file (used in the model)
   */
  def fromSource(sourceText: String, sourceName: String): DocFile = {
    val parser = new JJOnionParser(new StringReader(sourceText))
    parser.enableErrorRecovery(100)
    val unit = parser.unit()
    val comments = scanDocComments(sourceText)
    val types = unit.toplevels.collect {
      case c: AST.ClassDeclaration     => buildClass(c, comments)
      case i: AST.InterfaceDeclaration => buildInterface(i, comments)
      case r: AST.RecordDeclaration    => buildRecord(r, comments)
    }
    DocFile(sourceName, types)
  }

  // ---- Doc-comment scanning & association -----------------------------------

  /**
   * A raw doc block scanned from the source text: the ending source line of the
   * block and its raw contents (including the doc-comment delimiters).
   */
  private final case class RawDoc(endLine: Int, raw: String)

  /**
   * Scan the raw source for doc-comment blocks (starting with exactly a doc-comment opener,
   * not an ordinary block-comment opener), recording the line where each block ENDS.
   *
   * Returns a map from the declaration line (the next non-blank, non-comment
   * line after a block) to its parsed [[DocComment]].
   */
  private def scanDocComments(src: String): Map[Int, DocComment] = {
    val text = src.replace("\r\n", "\n").replace("\r", "\n")
    val n = text.length
    val blocks = scala.collection.mutable.ListBuffer[RawDoc]()

    var i = 0
    var line = 1
    while (i < n) {
      val c = text.charAt(i)
      if (c == '\n') { line += 1; i += 1 }
      else if (text.startsWith("/**", i) && !text.startsWith("/**/", i)) {
        val start = i
        val startLine = line
        // find the closing */
        var j = i + 3
        var curLine = startLine
        while (j < n && !text.startsWith("*/", j)) {
          if (text.charAt(j) == '\n') curLine += 1
          j += 1
        }
        val endLine = curLine
        val end = if (j < n) j + 2 else n
        blocks += RawDoc(endLine, text.substring(start, end))
        // advance
        i = end
        line = endLine
      } else if (text.startsWith("//", i)) {
        // line comment: skip to EOL
        while (i < n && text.charAt(i) != '\n') i += 1
      } else if (text.startsWith("/*", i)) {
        // ordinary block comment: skip
        var j = i + 2
        while (j < n && !text.startsWith("*/", j)) {
          if (text.charAt(j) == '\n') line += 1
          j += 1
        }
        i = if (j < n) j + 2 else n
      } else {
        i += 1
      }
    }

    // For each block, find the next non-blank line after endLine.
    val lines = text.split("\n", -1)
    def firstCodeLineAfter(after: Int): Int = {
      var k = after // 1-based; look at line index `after` (i.e. the line after block-end)
      while (k < lines.length) {
        val content = if (k - 1 < lines.length) lines(k - 1) else ""
        val t = content.trim
        if (t.nonEmpty && !t.startsWith("//") && !t.startsWith("*")) return k
        k += 1
      }
      -1
    }

    val result = scala.collection.mutable.Map[Int, DocComment]()
    for (b <- blocks) {
      val declLine = firstCodeLineAfter(b.endLine + 1)
      if (declLine > 0) result(declLine) = DocComment.parse(b.raw)
    }
    result.toMap
  }

  private def docFor(comments: Map[Int, DocComment], loc: onion.compiler.Location): Option[DocComment] =
    if (loc == null) None else comments.get(loc.line)

  // ---- Type builders --------------------------------------------------------

  private def buildClass(c: AST.ClassDeclaration, comments: Map[Int, DocComment]): DocType = {
    val members = (c.defaultSection.toList ++ c.sections).flatMap(_.members)
    val (ctors, methods, fields) = partitionMembers(members, comments)
    DocType(
      "class", c.name, SignatureRenderer.renderClass(c), locLine(c.location),
      docFor(comments, c.location), ctors, methods, fields
    )
  }

  private def buildInterface(i: AST.InterfaceDeclaration, comments: Map[Int, DocComment]): DocType = {
    val methods = i.methods.map(m =>
      DocMember("method", m.name, SignatureRenderer.renderMethod(m), locLine(m.location), docFor(comments, m.location))
    )
    DocType(
      "interface", i.name, SignatureRenderer.renderInterface(i), locLine(i.location),
      docFor(comments, i.location), Nil, methods, Nil
    )
  }

  private def buildRecord(r: AST.RecordDeclaration, comments: Map[Int, DocComment]): DocType = {
    // Record components render as fields.
    val fields = r.args.map(a =>
      DocMember("field", a.name, s"${a.name}: ${SignatureRenderer.renderType(a.typeRef)}", locLine(a.location), docFor(comments, a.location))
    )
    DocType(
      "record", r.name, SignatureRenderer.renderRecord(r), locLine(r.location),
      docFor(comments, r.location), Nil, Nil, fields
    )
  }

  private def partitionMembers(
    members: List[AST.MemberDeclaration],
    comments: Map[Int, DocComment]
  ): (List[DocMember], List[DocMember], List[DocMember]) = {
    val ctors  = scala.collection.mutable.ListBuffer[DocMember]()
    val methods = scala.collection.mutable.ListBuffer[DocMember]()
    val fields = scala.collection.mutable.ListBuffer[DocMember]()
    members.foreach {
      case m: AST.MethodDeclaration =>
        methods += DocMember("method", m.name, SignatureRenderer.renderMethod(m), locLine(m.location), docFor(comments, m.location))
      case f: AST.FieldDeclaration =>
        fields += DocMember("field", f.name, SignatureRenderer.renderField(f), locLine(f.location), docFor(comments, f.location))
      case d: AST.DelegatedFieldDeclaration =>
        fields += DocMember("field", d.name, SignatureRenderer.renderDelegatedField(d), locLine(d.location), docFor(comments, d.location))
      case ct: AST.ConstructorDeclaration =>
        ctors += DocMember("constructor", "new", SignatureRenderer.renderConstructor(ct), locLine(ct.location), docFor(comments, ct.location))
      case _ => // ignore other member kinds
    }
    (ctors.toList, methods.toList, fields.toList)
  }

  private def locLine(loc: onion.compiler.Location): Int = if (loc == null) 0 else loc.line
}
