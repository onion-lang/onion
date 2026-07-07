package onion.tools.doc

/**
 * A minimal, self-contained markdown-to-HTML renderer.
 *
 * Supported constructs:
 *  - ATX headings `#`..`######`
 *  - `**bold**`, `*italic*` / `_italic_`
 *  - inline `` `code` ``
 *  - fenced code blocks ```` ```lang ... ``` ```` -> `<pre><code>`
 *  - unordered lists (`-` or `*`) and simple ordered lists (`1.`)
 *  - links `[text](url)`
 *  - paragraphs separated by blank lines
 *
 * All literal text is HTML-escaped (`&`, `<`, `>`). No external dependencies.
 */
object Markdown {

  /** HTML-escape the ampersand and angle brackets in literal text. */
  def escape(s: String): String =
    s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

  def toHtml(src: String): String = {
    if (src == null) return ""
    val lines = src.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1).toList
    val out = new StringBuilder
    renderBlocks(lines, out)
    out.toString
  }

  private def renderBlocks(lines: List[String], out: StringBuilder): Unit = {
    var rest = lines
    while (rest.nonEmpty) {
      val line = rest.head
      val trimmed = line.trim
      if (trimmed.isEmpty) {
        rest = rest.tail
      } else if (isFenceStart(trimmed)) {
        val lang = trimmed.stripPrefix("```").trim
        val (code, after) = collectFenced(rest.tail)
        val cls = if (lang.isEmpty) "" else s""" class="language-${escape(lang)}""""
        out.append(s"<pre><code$cls>")
        out.append(escape(code.mkString("\n")))
        out.append("</code></pre>\n")
        rest = after
      } else if (headingLevel(trimmed) > 0) {
        val level = headingLevel(trimmed)
        val text = trimmed.dropWhile(_ == '#').trim
        out.append(s"<h$level>${renderInline(text)}</h$level>\n")
        rest = rest.tail
      } else if (isUnorderedItem(trimmed)) {
        val (items, after) = collectList(rest, isUnorderedItem, unorderedContent)
        out.append("<ul>\n")
        items.foreach(i => out.append(s"<li>${renderInline(i)}</li>\n"))
        out.append("</ul>\n")
        rest = after
      } else if (isOrderedItem(trimmed)) {
        val (items, after) = collectList(rest, isOrderedItem, orderedContent)
        out.append("<ol>\n")
        items.foreach(i => out.append(s"<li>${renderInline(i)}</li>\n"))
        out.append("</ol>\n")
        rest = after
      } else {
        // paragraph: consume until blank line or a block-starting line
        val (para, after) = collectParagraph(rest)
        out.append(s"<p>${renderInline(para.mkString(" "))}</p>\n")
        rest = after
      }
    }
  }

  private def isFenceStart(s: String): Boolean = s.startsWith("```")

  private def collectFenced(lines: List[String]): (List[String], List[String]) = {
    val code = scala.collection.mutable.ListBuffer[String]()
    var rest = lines
    while (rest.nonEmpty && !rest.head.trim.startsWith("```")) {
      code += rest.head
      rest = rest.tail
    }
    // drop the closing fence if present
    if (rest.nonEmpty) rest = rest.tail
    (code.toList, rest)
  }

  private def headingLevel(s: String): Int = {
    val hashes = s.takeWhile(_ == '#').length
    if (hashes >= 1 && hashes <= 6 && s.length > hashes && s.charAt(hashes) == ' ') hashes
    else 0
  }

  private def isUnorderedItem(s: String): Boolean =
    (s.startsWith("- ") || s.startsWith("* "))
  private def unorderedContent(s: String): String = s.trim.substring(2).trim

  private val orderedRe = """^(\d+)\.\s+(.*)$""".r
  private def isOrderedItem(s: String): Boolean = orderedRe.findFirstIn(s.trim).isDefined
  private def orderedContent(s: String): String = s.trim match {
    case orderedRe(_, content) => content
    case other                 => other
  }

  private def collectList(
    lines: List[String],
    isItem: String => Boolean,
    content: String => String
  ): (List[String], List[String]) = {
    val items = scala.collection.mutable.ListBuffer[String]()
    var rest = lines
    while (rest.nonEmpty && isItem(rest.head.trim)) {
      items += content(rest.head)
      rest = rest.tail
    }
    (items.toList, rest)
  }

  private def collectParagraph(lines: List[String]): (List[String], List[String]) = {
    val para = scala.collection.mutable.ListBuffer[String]()
    var rest = lines
    while (
      rest.nonEmpty && {
        val t = rest.head.trim
        t.nonEmpty && headingLevel(t) == 0 && !isFenceStart(t) &&
        !isUnorderedItem(t) && !isOrderedItem(t)
      }
    ) {
      para += rest.head.trim
      rest = rest.tail
    }
    (para.toList, rest)
  }

  /**
   * Render inline markup within a single logical line. Inline `code` spans are
   * escaped and protected from further processing; everything else is escaped
   * and then decorated for bold/italic/links.
   */
  private def renderInline(text: String): String = {
    val sb = new StringBuilder
    var i = 0
    val n = text.length
    while (i < n) {
      val c = text.charAt(i)
      if (c == '`') {
        val end = text.indexOf('`', i + 1)
        if (end >= 0) {
          val code = text.substring(i + 1, end)
          sb.append(s"<code>${escape(code)}</code>")
          i = end + 1
        } else {
          sb.append(escape("`"))
          i += 1
        }
      } else if (c == '[') {
        val (rendered, next) = tryLink(text, i)
        if (rendered != null) { sb.append(rendered); i = next }
        else { sb.append(escape("[")); i += 1 }
      } else if (text.startsWith("**", i)) {
        val end = text.indexOf("**", i + 2)
        if (end >= 0) {
          sb.append(s"<strong>${renderInline(text.substring(i + 2, end))}</strong>")
          i = end + 2
        } else { sb.append(escape("*")); i += 1 }
      } else if (c == '*' || c == '_') {
        val end = text.indexOf(c, i + 1)
        if (end > i + 1) {
          sb.append(s"<em>${renderInline(text.substring(i + 1, end))}</em>")
          i = end + 1
        } else { sb.append(escape(c.toString)); i += 1 }
      } else {
        sb.append(escape(c.toString))
        i += 1
      }
    }
    sb.toString
  }

  /** Attempt to parse `[text](url)` starting at `start`; returns (html, nextIndex) or (null, start). */
  private def tryLink(text: String, start: Int): (String, Int) = {
    val closeBracket = text.indexOf(']', start + 1)
    if (closeBracket < 0 || closeBracket + 1 >= text.length || text.charAt(closeBracket + 1) != '(')
      return (null, start)
    val closeParen = text.indexOf(')', closeBracket + 2)
    if (closeParen < 0) return (null, start)
    val label = text.substring(start + 1, closeBracket)
    val url = text.substring(closeBracket + 2, closeParen)
    val html = s"""<a href="${escape(url)}">${renderInline(label)}</a>"""
    (html, closeParen + 1)
  }
}
