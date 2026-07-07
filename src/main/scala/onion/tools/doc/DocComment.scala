package onion.tools.doc

/**
 * A parsed javadoc-style tag such as `@param`, `@return`, `@throws`, etc.
 *
 * @param kind  the tag name without the leading `@` (e.g. "param", "return")
 * @param arg   the first token after the tag when the tag takes a name/type
 *              (e.g. the parameter name for `@param`), otherwise empty
 * @param desc  the remaining description text
 */
final case class DocTag(kind: String, arg: String, desc: String)

/**
 * A parsed doc-comment documentation comment.
 *
 * @param summary the text up to the first blank line or first tag
 * @param body    the full description text preceding any tags (markdown source)
 * @param tags    the collected `@`-tags in source order
 */
final case class DocComment(summary: String, body: String, tags: List[DocTag]) {
  def params: List[DocTag]   = tags.filter(_.kind == "param")
  def returns: Option[DocTag] = tags.find(_.kind == "return")
  def throwsTags: List[DocTag] =
    tags.filter(t => t.kind == "throws" || t.kind == "exception")
  def see: List[DocTag]       = tags.filter(_.kind == "see")
  def since: Option[DocTag]   = tags.find(_.kind == "since")
  def deprecated: Option[DocTag] = tags.find(_.kind == "deprecated")
  def author: List[DocTag]    = tags.filter(_.kind == "author")
}

object DocComment {

  /** Tags whose first token is a name/type consumed into `arg`. */
  private val taggedWithArg = Set("param", "throws", "exception")

  /**
   * Parse a raw doc-comment comment block into a [[DocComment]]. The input may
   * or may not include the delimiters; both forms are handled.
   */
  def parse(raw: String): DocComment = {
    val lines = cleanLines(raw)

    // Split into body lines (before the first tag) and tag lines.
    val bodyLines = scala.collection.mutable.ListBuffer[String]()
    val tags = scala.collection.mutable.ListBuffer[DocTag]()

    var current: Option[StringBuilder] = None
    var currentKind = ""
    var currentArg = ""
    var inTags = false

    def flush(): Unit = current.foreach { sb =>
      tags += DocTag(currentKind, currentArg, sb.toString.trim)
    }

    for (line <- lines) {
      val trimmed = line.trim
      if (trimmed.startsWith("@")) {
        flush()
        inTags = true
        val afterAt = trimmed.substring(1)
        val (kind, remainder) = splitFirst(afterAt)
        currentKind = kind
        if (taggedWithArg.contains(kind)) {
          val (arg, rest) = splitFirst(remainder)
          currentArg = arg
          current = Some(new StringBuilder(rest))
        } else {
          currentArg = ""
          current = Some(new StringBuilder(remainder))
        }
      } else if (inTags) {
        // continuation of the current tag description
        current.foreach { sb => sb.append(" ").append(trimmed) }
      } else {
        bodyLines += line
      }
    }
    flush()

    val body = bodyLines.mkString("\n").trim
    val summary = extractSummary(body)
    DocComment(summary, body, tags.toList)
  }

  /** Strip the the delimiters, and a leading ` * ` from each line. */
  private def cleanLines(raw: String): List[String] = {
    if (raw == null) return Nil
    var text = raw.trim
    if (text.startsWith("/**")) text = text.substring(3)
    else if (text.startsWith("/*")) text = text.substring(2)
    if (text.endsWith("*/")) text = text.substring(0, text.length - 2)
    text.replace("\r\n", "\n").replace("\r", "\n").split("\n", -1).toList.map(stripLeadingStar)
  }

  /** Remove a leading `*` (with optional surrounding spaces) from a line. */
  private def stripLeadingStar(line: String): String = {
    val l = line.replaceAll("^\\s+", "")
    if (l.startsWith("* ")) l.substring(2)
    else if (l == "*") ""
    else if (l.startsWith("*")) l.substring(1)
    else line.replaceAll("^\\s?", "") // preserve inner content, drop at most one leading space
  }

  /** The summary is the text up to the first blank line. */
  private def extractSummary(body: String): String = {
    if (body.isEmpty) return ""
    val idx = body.indexOf("\n\n")
    val firstPara = if (idx >= 0) body.substring(0, idx) else body
    firstPara.replace("\n", " ").trim
  }

  /** Split off the first whitespace-delimited token, returning (token, rest). */
  private def splitFirst(s: String): (String, String) = {
    val t = s.trim
    val sp = t.indexWhere(_.isWhitespace)
    if (sp < 0) (t, "") else (t.substring(0, sp), t.substring(sp).trim)
  }
}
