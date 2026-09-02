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
  // A raised value shaped like a constructor call (`raise Exception("boom")`)
  // gets a `throw new` suggestion; anything else (e.g. a bare re-raise like
  // `raise e`) falls through to the plain `throw` case below it, so this must
  // be tried first.
  private val LeadingRaiseConstructorCall =
    """^\s*raise\s+([A-Za-z_][\w.]*)\s*\(([^()]*)\)\s*$""".r
  private val LeadingRaiseStatement = """^\s*raise\s+(.+?)\s*$""".r
  // A JavaScript/Python-style prefix `await expr` statement -- Onion's `Future`
  // is awaited postfix (`f.await()`), so this reads as a bare `await` identifier
  // followed by another expression with nothing in between, which also matches
  // the generic missing-call-parens fallback (suggesting the nonsensical
  // `await(...)`); keep this ahead of that case.
  private val LeadingAwaitStatement = """^\s*await\s+(.+?)\s*$""".r
  // A JavaScript/Python-style generator `yield expr` statement -- Onion has no
  // generator/coroutine construct, so this reads as a bare `yield` identifier
  // followed by another expression with nothing in between, which also matches
  // the generic missing-call-parens fallback (suggesting the nonsensical
  // `yield(...)`); keep this ahead of that case.
  private val LeadingYieldStatement = """^\s*yield\s+(.+?)\s*$""".r
  // A Go-style prefix `defer expr` statement -- Onion has no `defer`, so this
  // reads as a bare `defer` identifier followed by another expression with
  // nothing in between, which also matches the generic missing-call-parens
  // fallback (suggesting the nonsensical `defer(...)`); keep this ahead of
  // that case.
  private val LeadingDeferStatement = """^\s*defer\s+(.+?)\s*$""".r

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
    } else if (LeadingRaiseConstructorCall.findFirstMatchIn(sourceLine).isDefined) {
      val matched = LeadingRaiseConstructorCall.findFirstMatchIn(sourceLine).get
      hint("error.parsing.hint.python_style_raise_construct", matched.group(1), matched.group(2).trim)
    } else if (LeadingRaiseStatement.findFirstMatchIn(sourceLine).isDefined) {
      val matched = LeadingRaiseStatement.findFirstMatchIn(sourceLine).get
      hint("error.parsing.hint.python_style_raise", matched.group(1))
    } else if (LeadingAwaitStatement.findFirstMatchIn(sourceLine).isDefined) {
      val matched = LeadingAwaitStatement.findFirstMatchIn(sourceLine).get
      hint("error.parsing.hint.await_not_supported", matched.group(1).trim)
    } else if (LeadingYieldStatement.findFirstMatchIn(sourceLine).isDefined) {
      hint("error.parsing.hint.yield_not_supported")
    } else if (LeadingDeferStatement.findFirstMatchIn(sourceLine).isDefined) {
      val matched = LeadingDeferStatement.findFirstMatchIn(sourceLine).get
      hint("error.parsing.hint.defer_not_supported", matched.group(1).trim)
    } else {
      None
    }
  }

  private def hint(messageKey: String, arguments: String*): Option[SyntaxHint] =
    Some(SyntaxHint(messageKey, arguments))
}
