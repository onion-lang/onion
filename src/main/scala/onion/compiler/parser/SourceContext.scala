package onion.compiler.parser

private[compiler] final case class SourceContext(context: String, sourceLine: String)

private[compiler] object SourceContext {
  private val ContextLimit = 200

  /**
   * Extract hint input from a 1-based source position.
   *
   * `context` starts at the reported column and may cross line boundaries, while
   * `sourceLine` contains the entire reported line. Context is bounded so syntax
   * hint matching stays predictable for pathological input.
   */
  def at(sourceText: String, line: Int, column: Int): SourceContext = {
    val lineStart = lineStartOffset(sourceText, line)
    val contextStart = math.min(sourceText.length, lineStart + column - 1)
    val lineEnd = sourceText.indexOf('\n', lineStart) match {
      case -1 => sourceText.length
      case index => index
    }

    SourceContext(
      context = sourceText.substring(
        contextStart,
        math.min(sourceText.length, contextStart + ContextLimit)
      ),
      sourceLine = sourceText.substring(lineStart, lineEnd)
    )
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
