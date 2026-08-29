package onion.compiler.parser

/** Classifies unsupported control-flow forms that have direct Onion alternatives. */
private[compiler] object ControlFlowSyntaxHints {
  private val LeadingSwitchStatement = """^\s*switch\b""".r
  private val LeadingWhenStatement = """^\s*when\b""".r
  private val LeadingMatchStatement = """^\s*match\b""".r
  private val DefaultCaseLabel = """^\s*default\s*:""".r
  private val ElifStatement = """\belif\b""".r
  private val ElsifStatement = """\belsif\b""".r
  private val ElseifStatement = """\belseif\b""".r
  private val LeadingUnlessStatement = """^\s*unless\b""".r
  private val LeadingUntilStatement = """^\s*until\b""".r
  private val ExceptClause = """\bexcept\b""".r

  def classify(found: String, sourceLine: String): Option[SyntaxHint] = {
    if (LeadingSwitchStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.switch_not_supported")
    } else if (found == "when" && LeadingWhenStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.when_not_supported")
    } else if (LeadingMatchStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.match_not_supported")
    } else if (DefaultCaseLabel.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.default_case_not_supported")
    } else if (ElifStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.elif_not_supported")
    } else if (ElsifStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.elsif_not_supported")
    } else if (ElseifStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.elseif_not_supported")
    } else if (LeadingUnlessStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.unless_not_supported")
    } else if (LeadingUntilStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.until_not_supported")
    } else if (ExceptClause.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.except_not_supported")
    } else {
      None
    }
  }

  private def hint(messageKey: String): Option[SyntaxHint] =
    Some(SyntaxHint(messageKey))
}
