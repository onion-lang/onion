package onion.compiler.parser

private[compiler] final case class SyntaxHint(
  messageKey: String,
  arguments: Seq[String] = Seq.empty
)

/**
 * Classifies common foreign-syntax mistakes without parser or locale state.
 *
 * Rule order is observable: several specific mistakes also match a broader
 * fallback. Keep the priority cases covered by SyntaxHintClassifierSpec before
 * their broader neighbors when adding a rule.
 */
private[compiler] object SyntaxHintClassifier {
  private val ParenthesizedTrailingLambdaHead = """\A\{\s*\(([^()]*)\)\s*->""".r
  private val OldArrowTrailingLambdaHead = """\A\{\s*(?:\(?[^(){}=]*\)?)\s*=>""".r
  private val LeadingSwitchStatement = """^\s*switch\b""".r
  private val LeadingWhenStatement = """^\s*when\b""".r
  private val LeadingMatchStatement = """^\s*match\b""".r
  private val DefaultCaseLabel = """^\s*default\s*:""".r
  private val ElifStatement = """\belif\b""".r
  private val ExceptClause = """\bexcept\b""".r
  private val LeadingLetDeclaration = """^\s*let\s+[A-Za-z_]\w*\b""".r
  private val FunDeclaration = """\b(?:fun|func|fn|function)\s+[A-Za-z_]\w*\s*\(""".r
  private val FunExtensionDeclaration =
    """\b(?:fun|func|fn|function)\s+([A-Za-z_]\w*)\.([A-Za-z_]\w*)\s*\(""".r
  private val ParenthesizedCatchClause = """\bcatch\s*\(""".r
  private val CStyleForLoop = """^\s*for\s*\(""".r
  private val ForEachDestructureMistake =
    """^\s*for\s*\(?\s*([A-Za-z_]\w*(?:\s*,\s*[A-Za-z_]\w*)+)\s*\)?\s*in\s+([^{]+?)\s*\{?\s*$""".r
  private val JavaStyleEnhancedForLoop =
    """^\s*for\s*\(\s*(?:final\s+)?([A-Za-z_][\w.\[\],\s]*\??)\s+([A-Za-z_]\w*)\s*:\s*(.+?)\s*\)\s*\{?\s*$""".r
  private val ForeachParenMistake = """\bforeach\s*\(""".r
  private val JavaStyleImport = """^\s*import\s+(?!\{)\S""".r
  private val JavaStyleImportAlias =
    """^\s*([A-Za-z_$][\w$]*)\s*=\s*([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*(?:\.\*)?)\s*;?\s*$""".r
  private val JavaStyleConstructor =
    """^\s*(?:public|private|protected)?\s*[A-Z][A-Za-z0-9_]*\s*\(""".r
  private val JavaStyleMethodDeclaration =
    """^\s*(?:public|private|protected)?\s*(?!public\b|private\b|protected\b|def\b)[A-Za-z_]\w*\s+[A-Za-z_]\w*\s*\(""".r
  private val MissingParameterType =
    """\bdef\s+[A-Za-z_]\w*(?:\[[^\]]*\])?\s*\(\s*(?:(?:val|var)\s+)?([A-Za-z_]\w*)\s*[,)]""".r
  private val JavaStyleFieldDeclaration =
    """^\s*(?:public|private|protected)?\s*([A-Z][A-Za-z0-9_]*(?:\[[^\]]*\])?\??)\s+([A-Za-z_]\w*)\s*(?:=|;|$)""".r
  private val JavaStyleTypeCase =
    """^\s*case\s+[a-z_]\w*\s*:\s*[A-Z][\w.\[\]]*\??\s*:""".r
  private val JavaStyleGenericAngleBrackets =
    """\b([A-Z][A-Za-z0-9_]*)<([A-Za-z_][\w\s,\[\]?]*)>""".r
  private val PrimitiveTypeNames =
    Set("Int", "Long", "Double", "Float", "Boolean", "Byte", "Short", "Char")
  private val DotAfterPrimitiveTypeName = """\A[A-Za-z]+\s*\.\s*([A-Za-z_]\w*)""".r
  private val JavaStyleRecordBody =
    """^\s*record\s+([A-Za-z_]\w*)(?:\[[^\]]*\])?\s*\{""".r
  private val JavaStyleTryResource =
    """\btry\s*\(\s*([A-Z][A-Za-z0-9_]*(?:\[[^\]]*\])?\??)\s+([A-Za-z_]\w*)\s*=""".r
  private val JavaStyleImplements =
    """^\s*class\s+([A-Za-z_]\w*)\s*(?:\([^)]*\))?\s*(?:extends\s+[A-Za-z_][\w.\[\]]*(?:\([^)]*\))?\s*)?implements\s+([A-Za-z_][\w.\[\], ]*?)\s*\{?\s*$""".r
  private val JavaStyleAnnotatedMethod =
    """\A@([A-Za-z_]\w*)\s+(?:public|private|protected)?\s*(?!public\b|private\b|protected\b)[A-Za-z_]\w*\s+[A-Za-z_]\w*\s*\(""".r
  private val ReservedWords = Set(
    "abstract", "and", "as", "Boolean", "break", "Byte", "case", "catch", "Char", "class",
    "const", "continue", "def", "do", "Double", "else", "enum", "extends", "extension",
    "false", "final", "finally", "Float", "for", "foreach", "forward", "goto", "if",
    "import", "instance", "interface", "internal", "Int", "is", "Long", "module", "new",
    "null", "or", "override", "private", "protected", "public", "record", "ret", "return",
    "select", "self", "Short", "static", "super", "sealed", "synchronized", "this", "throw",
    "throws", "trait", "true", "try", "type", "Unit", "val", "var", "void", "volatile",
    "when", "while"
  )
  private val NullableUnionType =
    """:\s*([A-Za-z_][\w.\[\]]*)\s*\|\s*null\b""".r
  private val CStyleCast =
    """\(\s*([A-Z][\w.\[\]]*\??)\s*\)\s*[A-Za-z_$(]""".r
  private val PythonStyleReturnArrow =
    """\bdef\s+([A-Za-z_]\w*)\s*(?:\[[^\]]*\])?\s*\([^()]*\)\s*->\s*([A-Za-z_][\w.\[\]?]*)""".r

  def classify(
    found: String,
    expected: String,
    context: String,
    sourceLine: String
  ): Option[SyntaxHint] =
    found match {
      case "<:" =>
        hint("error.parsing.hint.old_conforms")
      case "const" =>
        hint("error.parsing.hint.js_style_const")
      case _ if JavaStyleImplements.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleImplements.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_implements", matched.group(1), matched.group(2).trim)
      case _ if found.startsWith("@") && JavaStyleAnnotatedMethod.findFirstMatchIn(context).isDefined =>
        val matched = JavaStyleAnnotatedMethod.findFirstMatchIn(context).get
        hint("error.parsing.hint.java_style_annotated_method", matched.group(1))
      case ":" if JavaStyleTypeCase.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.case_type_colon")
      case "<" if JavaStyleGenericAngleBrackets.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleGenericAngleBrackets.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_generics", matched.group(1), matched.group(2).trim)
      case ":" if expected.contains("extends") =>
        hint("error.parsing.hint.old_extends")
      case "(" if expected.contains("\"this\"") && !expected.contains("<ID>") =>
        hint("error.parsing.hint.old_super_init")
      case "(" if expected.contains("<ID>") && ParenthesizedCatchClause.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.catch_parens")
      // Specific foreach syntax must win over the generic removed `for ... in` form.
      case "in" if ForeachParenMistake.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.foreach_parens")
      case "in" =>
        hint("error.parsing.hint.old_for_in")
      case _ if LeadingSwitchStatement.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.switch_not_supported")
      case "when" if LeadingWhenStatement.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.when_not_supported")
      case _ if LeadingMatchStatement.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.match_not_supported")
      case _ if DefaultCaseLabel.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.default_case_not_supported")
      case _ if ElifStatement.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.elif_not_supported")
      case _ if ExceptClause.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.except_not_supported")
      case _ if LeadingLetDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.js_style_let")
      // Also resembles `Int.member`; extension-declaration advice is more useful.
      case _ if FunExtensionDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = FunExtensionDeclaration.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.fun_extension_declaration", matched.group(1), matched.group(2))
      case _ if FunDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.fun_declaration")
      case name if PrimitiveTypeNames.contains(name) && DotAfterPrimitiveTypeName.findFirstMatchIn(context).isDefined =>
        val member = DotAfterPrimitiveTypeName.findFirstMatchIn(context).get.group(1)
        hint("error.parsing.hint.primitive_dot_static", name, member)
      // The parenthesized form also matches CStyleForLoop, so keep this first.
      case _ if ForEachDestructureMistake.findFirstMatchIn(sourceLine).isDefined =>
        val matched = ForEachDestructureMistake.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.for_each_destructure", matched.group(1), matched.group(2).trim)
      // A Java enhanced-for (`for (Type name : expr)`) also matches CStyleForLoop, so keep this first.
      case _ if JavaStyleEnhancedForLoop.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleEnhancedForLoop.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_enhanced_for", matched.group(2), matched.group(1).trim, matched.group(3).trim)
      case _ if CStyleForLoop.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.c_style_for")
      case _ if JavaStyleImport.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.java_style_import")
      case "=" if expected == "\".\"" && JavaStyleImportAlias.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleImportAlias.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_import_alias", matched.group(2), matched.group(1))
      case _ if (expected == "\":\"" || expected.contains("\"def\"")) && JavaStyleMethodDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.java_style_method")
      case _ if (expected == "\":\"" || expected.contains("\"def\"")) && JavaStyleConstructor.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.java_style_constructor")
      case (")" | ",") if expected == "\":\"" && MissingParameterType.findFirstMatchIn(sourceLine).isDefined =>
        val matched = MissingParameterType.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.missing_parameter_type", matched.group(1))
      case _ if JavaStyleFieldDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleFieldDeclaration.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_field", matched.group(1), matched.group(2))
      case "{" if expected.contains("\"(\"") && JavaStyleRecordBody.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleRecordBody.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_record_body", matched.group(1))
      case "(" if expected.contains("{") && JavaStyleTryResource.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleTryResource.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_try_resource", matched.group(1), matched.group(2))
      case "|" if NullableUnionType.findFirstMatchIn(sourceLine).isDefined =>
        val matched = NullableUnionType.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.nullable_union_type", matched.group(1))
      case _ if CStyleCast.findFirstMatchIn(sourceLine).isDefined =>
        val matched = CStyleCast.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.c_style_cast", matched.group(1))
      case "->" if PythonStyleReturnArrow.findFirstMatchIn(sourceLine).isDefined =>
        val matched = PythonStyleReturnArrow.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.python_style_return_arrow", matched.group(1), matched.group(2))
      case "?" =>
        hint("error.parsing.hint.ternary")
      case "else" =>
        hint("error.parsing.hint.dangling_else")
      // The parentheses rule below recognizes only the current `->` arrow.
      case "{" if OldArrowTrailingLambdaHead.findFirstMatchIn(context).isDefined =>
        hint("error.parsing.hint.old_trailing_arrow")
      // A standalone (non-trailing) lambda with the old `=>` arrow: `found` lands on
      // the `=` and `context` (which starts exactly there) sees the rest, `=> ...`.
      // A map/array literal's Ruby-style `key => value` mistake hits the same token,
      // but the parser still wants `:` there, so let that reading win instead.
      case "=" if context.startsWith("=>") && !expected.contains("\":\"") =>
        hint("error.parsing.hint.old_lambda_arrow")
      case "{" if ParenthesizedTrailingLambdaHead.findFirstMatchIn(context).isDefined =>
        val params = ParenthesizedTrailingLambdaHead.findFirstMatchIn(context).get.group(1).trim
        hint("error.parsing.hint.trailing_lambda_parens", params)
      case word if ReservedWords.contains(word) && (expected.contains("<ID>") || expected.contains("<QUOTED_ID>")) =>
        hint("error.parsing.hint.reserved_word_identifier", word)
      case _ if expected.contains("{") && !Set(";", "<EOL>", "<EOF>").exists(expected.contains) =>
        hint("error.parsing.hint.block_expected")
      case _ =>
        None
    }

  private def hint(messageKey: String, arguments: String*): Option[SyntaxHint] =
    Some(SyntaxHint(messageKey, arguments))
}
