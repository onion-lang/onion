package onion.compiler.verification

/**
 * How a law counterexample (E0064) is printed.
 *
 * A String is shown as an Onion string literal, quoted and escaped: the law samples include
 * a newline, a tab and delimiters (#2513), and printed raw a newline split the diagnostic
 * over two lines, a tab showed as blank space and `Row(name=,, n=1)` did not show where
 * `name` ends. A record whose `toString` is the synthesized one is shown component by
 * component through the same rules, so `Row(name="\n", n=1)`; any other value keeps its
 * own `toString`.
 */
private[compiler] object CounterexampleText {

  /** Nesting beyond this falls back to `toString`; generated records stop at depth 3. */
  private val MaxDepth = 8

  /**
   * Renders one argument.
   *
   * @param records component names of each record class whose `toString` is synthesized,
   *                keyed by JVM class name
   */
  def render(value: AnyRef, records: Map[String, Seq[String]]): String =
    render(value, records, 0)

  private def render(value: AnyRef, records: Map[String, Seq[String]], depth: Int): String =
    value match {
      case null      => "null"
      case s: String => stringLiteral(s)
      case _ =>
        val clazz = value.getClass
        records.get(clazz.getName) match {
          case Some(components) if depth < MaxDepth =>
            componentValues(value, clazz, components) match {
              case Some(values) =>
                val shown = components.zip(values).map { case (n, v) => s"$n=${render(v, records, depth + 1)}" }
                // The name the synthesized `toString` prints.
                val name = clazz.getName.substring(clazz.getName.lastIndexOf('.') + 1)
                s"$name(${shown.mkString(", ")})"
              case None => oneLine(String.valueOf(value))
            }
          case _ => oneLine(String.valueOf(value))
        }
    }

  private def componentValues(value: AnyRef, clazz: Class[?], components: Seq[String]): Option[Seq[AnyRef]] =
    try {
      Some(components.map { name =>
        val f = clazz.getDeclaredField(name)
        f.setAccessible(true)
        f.get(value)
      })
    } catch { case _: ReflectiveOperationException | _: RuntimeException => None }

  /**
   * `s` as an Onion string literal that reads back as `s`: `\n \t \r \b \f \" \\` use their
   * escapes, `#{` is escaped so it does not interpolate, and any other control character
   * becomes a `\uXXXX` escape. Printable text, non-ASCII included, is kept.
   */
  def stringLiteral(s: String): String = {
    val sb = new java.lang.StringBuilder(s.length + 2)
    sb.append('"')
    escapeInto(sb, s, quoted = true)
    sb.append('"').toString
  }

  /**
   * `s` with its control characters escaped as in [[stringLiteral]], but not quoted:
   * for text that is not an Onion value, such as an exception message.
   */
  def oneLine(s: String): String = {
    if (!s.exists(isControl)) return s
    val sb = new java.lang.StringBuilder(s.length + 8)
    escapeInto(sb, s, quoted = false)
    sb.toString
  }

  private def isControl(c: Char): Boolean = c < ' ' || (c >= '\u007f' && c <= '\u009f')

  private def escapeInto(sb: java.lang.StringBuilder, s: String, quoted: Boolean): Unit = {
    var i = 0
    while (i < s.length) {
      val c = s.charAt(i)
      c match {
        case '\n' => sb.append("\\n")
        case '\t' => sb.append("\\t")
        case '\r' => sb.append("\\r")
        case '\b' => sb.append("\\b")
        case '\f' => sb.append("\\f")
        case '"' if quoted  => sb.append("\\\"")
        case '\\' if quoted => sb.append("\\\\")
        case '#' if quoted && i + 1 < s.length && s.charAt(i + 1) == '{' => sb.append("\\#")
        case _ if isControl(c) =>
          // A unicode escape, decoded before the string is scanned. `\n` and `\r`, the
          // two characters that would end the literal there, have their own escapes above.
          sb.append(String.format("\\u%04x", Integer.valueOf(c.toInt)))
        case _ => sb.append(c)
      }
      i += 1
    }
  }
}
