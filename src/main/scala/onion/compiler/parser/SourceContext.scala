package onion.compiler.parser

private[compiler] final case class SourceContext(context: String, sourceLine: String, precedingText: String = "")

private[compiler] object SourceContext {
  private val ContextLimit = 200

  /**
   * Extract hint input from a 1-based source position.
   *
   * `context` starts at the reported column and may cross line boundaries, while
   * `sourceLine` contains the entire reported line. Context is bounded so syntax
   * hint matching stays predictable for pathological input. `precedingText` is
   * everything before the reported line, for hints that name an enclosing
   * declaration.
   */
  def at(sourceText: String, line: Int, column: Int): SourceContext = {
    val lineStart = lineStartOffset(sourceText, line)
    val lineEnd = sourceText.indexOf('\n', lineStart) match {
      case -1 => sourceText.length
      case index => index
    }
    val contextStart = columnOffset(sourceText, lineStart, lineEnd, column)

    SourceContext(
      context = sourceText.substring(
        contextStart,
        math.min(sourceText.length, contextStart + ContextLimit)
      ),
      sourceLine = sourceText.substring(lineStart, lineEnd),
      precedingText = sourceText.substring(0, lineStart)
    )
  }

  // Both parsers count a tab as advancing to the next multiple of 8, so a reported
  // column is a visual one; map it back to a character offset on the line.
  private val TabSize = 8

  private def columnOffset(sourceText: String, lineStart: Int, lineEnd: Int, column: Int): Int = {
    var offset = lineStart
    var visual = 0
    while (offset < lineEnd && visual < column - 1) {
      visual += (if (sourceText.charAt(offset) == '\t') TabSize - (visual % TabSize) else 1)
      offset += 1
    }
    offset
  }

  private def lineStartOffset(sourceText: String, line: Int): Int = {
    var offset = 0
    var currentLine = 1
    while (currentLine < line && offset >= 0 && offset < sourceText.length) {
      val newline = sourceText.indexOf('\n', offset)
      if (newline < 0) offset = sourceText.length
      else {
        offset = newline + 1
        currentLine += 1
      }
    }
    offset
  }
}
