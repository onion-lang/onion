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
  private val NotOperatorCondition = """^\s*(?:if|while|else\s+if)\s+not\b""".r
  private val TypeofCondition =
    """^\s*(?:if|while|else\s+if)\s+typeof\s+([A-Za-z_][\w.]*)""".r
  private val PythonStyleColonBlockHeader =
    """^\s*(if|while|else\s+if)\s+(.+?):\s*$""".r
  // A Python-style `class Foo:` (or `class Foo(x: Int):`) header, where the colon
  // terminates the whole line -- unlike the old `class Foo: Bar` extends-colon
  // mistake below, where a superclass name follows the colon on the same line.
  private val PythonStyleColonBlockClassHeader =
    """^\s*class\s+(.+?):\s*$""".r
  private val LeadingLetDeclaration = """^\s*let\s+[A-Za-z_]\w*\b""".r
  private val RustStyleMutDeclaration =
    """^\s*(val|var)\s+mut\s+([A-Za-z_]\w*)""".r
  private val IfWhileLetBinding =
    """^\s*(if|while)\s+let\s+(?:[A-Za-z_]\w*\s*\(\s*([A-Za-z_]\w*)\s*\)|([A-Za-z_]\w*))\s*=\s*(.+?)\s*\{?\s*$""".r
  private val GuardLetElseBinding =
    """^\s*guard\s+let\s+(?:[A-Za-z_]\w*\s*\(\s*([A-Za-z_]\w*)\s*\)|([A-Za-z_]\w*))\s*=\s*(.+?)\s*else\b.*$""".r
  // A Swift-style bare `guard <condition> else { ... }` early exit, with no
  // `let` binding -- tried only after GuardLetElseBinding above, so a `let`
  // form is never misread as a plain condition.
  private val GuardElseBinding =
    """^\s*guard\s+(.+?)\s*else\b.*$""".r
  private val GoStyleShortVarDecl =
    """^\s*([A-Za-z_]\w*)\s*:=\s*(.+?)\s*$""".r
  private val UsingResourceStatement =
    """^\s*using\s+([A-Za-z_]\w*)\s*=\s*(.+?)\s*\{?\s*$""".r
  private val CSharpStyleUsingImport =
    """^\s*using\s+([A-Za-z_]\w*(?:\.[A-Za-z_]\w*)*)\s*;\s*$""".r
  private val PythonStyleFromImport =
    """^\s*from\s+([A-Za-z_][\w.]*)\s+import\s+(.+?)\s*;?\s*$""".r
  private val DataClassDeclaration =
    """^\s*data\s+class\s+([A-Za-z_]\w*)\s*\(([^)]*)\)""".r
  // A Python-style `lambda x: expr` (or `lambda x, y: expr`) expression --
  // `lambda` isn't a reserved word, so it parses as a bare identifier and the
  // rest reads as a stray second statement with nothing connecting it back to
  // the actual mistake. The `\s+` before the parameter list requires at least
  // one space, which a real Onion identifier named `lambda` followed by its
  // own type annotation (`lambda: Int`, no space before the colon) never has,
  // so this can't misfire on that.
  private val PythonStyleLambdaExpression =
    """\blambda\s+([A-Za-z_]\w*(?:\s*,\s*[A-Za-z_]\w*)*)\s*:\s*(.+?)\s*$""".r
  private val RustStyleStructDeclaration =
    """^\s*struct\s+([A-Za-z_]\w*)\b""".r
  private val KotlinScalaStyleObjectDeclaration =
    """^\s*object\s+([A-Za-z_]\w*)\b""".r
  private val ValVarParamPrefix = """^(?:val|var)\s+""".r
  private val FunDeclaration = """\b(?:fun|func|fn|function)\s+[A-Za-z_]\w*\s*\(""".r
  private val FunExtensionDeclaration =
    """\b(?:fun|func|fn|function)\s+([A-Za-z_]\w*)\.([A-Za-z_]\w*)\s*\(""".r
  private val ParenthesizedCatchClause = """\bcatch\s*\(""".r
  private val CStyleForLoop = """^\s*for\s*\(""".r
  private val ForEachDestructureMistake =
    """^\s*for\s*\(?\s*([A-Za-z_]\w*(?:\s*,\s*[A-Za-z_]\w*)+)\s*\)?\s*in\s+([^{]+?)\s*\{?\s*$""".r
  // The whole `for (vars in coll)` clause wrapped in one outer paren pair: unlike
  // ForEachDestructureMistake's optional parens around the vars alone, the closing
  // paren here belongs to the wrapper, not to the collection expression, so it must
  // be matched explicitly -- otherwise it leaks into the captured collection text
  // (e.g. "entries)" instead of "entries").
  private val ForEachDestructureWrappedMistake =
    """^\s*for\s*\(\s*([A-Za-z_]\w*(?:\s*,\s*[A-Za-z_]\w*)+)\s*in\s+([^{]+?)\)\s*\{?\s*$""".r
  private val JavaStyleEnhancedForLoop =
    """^\s*for\s*\(\s*(?:final\s+)?([A-Za-z_][\w.\[\],\s]*\??)\s+([A-Za-z_]\w*)\s*:\s*(.+?)\s*\)\s*\{?\s*$""".r
  private val ForeachParenMistake = """\bforeach\s*\(""".r
  private val JavaStyleImport = """^\s*import\s+(?!\{)\S""".r
  private val JavaStyleImportAlias =
    """^\s*([A-Za-z_$][\w$]*)\s*=\s*([A-Za-z_$][\w$]*(?:\.[A-Za-z_$][\w$]*)*(?:\.\*)?)\s*;?\s*$""".r
  private val JavaStyleConstructor =
    """^\s*(?:public|private|protected)?\s*[A-Z][A-Za-z0-9_]*\s*\(""".r
  private val JsStyleConstructorDeclaration = """^\s*constructor\s*\(""".r
  private val JavaStyleMethodDeclaration =
    """^\s*(?:public|private|protected)?\s*(?!public\b|private\b|protected\b|def\b)[A-Za-z_]\w*\s+[A-Za-z_]\w*\s*\(""".r
  private val MissingParameterType =
    """\bdef\s+[A-Za-z_]\w*(?:\[[^\]]*\])?\s*\(\s*(?:(?:val|var)\s+)?([A-Za-z_]\w*)\s*[,)]""".r
  private val JavaStyleFieldDeclaration =
    """^\s*(?:public|private|protected)?\s*([A-Z][A-Za-z0-9_]*(?:\[[^\]]*\])?\??)\s+([A-Za-z_]\w*)\s*(?:=|;|$)""".r
  // `final` is itself a valid Onion modifier (for classes/methods, via `modifiers()`
  // in the grammar), so this must require a capitalized type name right after it --
  // that shape never appears for a legitimate `final class ...`/`final def ...`/
  // `final override ...` (all lowercase keywords), only for the Java mistake of a
  // `final`-qualified local variable, which Onion has no grammar production for at all.
  private val JavaStyleFinalLocalDeclaration =
    """^\s*final\s+([A-Z][A-Za-z0-9_]*(?:\[[^\]]*\])?\??)\s+([A-Za-z_]\w*)\s*(?:=|;|$)""".r
  private val JavaStyleTypeCase =
    """^\s*case\s+[a-z_]\w*\s*:\s*[A-Z][\w.\[\]]*\??\s*:""".r
  private val JavaStyleGenericAngleBrackets =
    """\b([A-Z][A-Za-z0-9_]*)<([A-Za-z_][\w\s,\[\]?]*)>""".r
  // A Java-style generic constructor call, e.g. `new ArrayList<String>()` or the
  // empty-diamond `new ArrayList<>()`. Unlike JavaStyleGenericAngleBrackets above
  // (a type-annotation position, where `<` is never valid and is itself the found
  // token), `<` here parses as a valid less-than operator: `new ArrayList` reads as
  // a value, then `<String>` as a comparison chain, so the real syntax error only
  // surfaces later in the line (typically at the trailing `(` or `)`) with no token
  // connecting it back to the angle brackets. Match on the source line directly,
  // independent of the found token, to still catch it.
  private val JavaStyleGenericConstructorCall =
    """\bnew\s+([A-Za-z_][\w.]*)\s*<\s*([^<>]*?)\s*>\s*\(""".r
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
  private val InstanceofExpression =
    """\b([A-Za-z_][\w.]*(?:\([^()]*\))?)\s+instanceof\s+([A-Za-z_][\w.\[\]]*)""".r
  private val PythonStyleReturnArrow =
    """\bdef\s+([A-Za-z_]\w*)\s*(?:\[[^\]]*\])?\s*\([^()]*\)\s*->\s*([A-Za-z_][\w.\[\]?]*)""".r
  private val DanglingReturnTypeColon =
    """\bdef\s+([A-Za-z_]\w*)\s*(?:\[[^\]]*\])?\s*\([^()]*\)\s*:\s*$""".r
  private val BareAdjacentTokens = """^\s*([A-Za-z_]\w*)\s+\S""".r
  // `context` starts exactly at the found token, so a lone `?` found immediately
  // followed by a second `?` is the two-token spelling of `??` -- distinguish it
  // from the unrelated `cond ? a : b` ternary mistake below, which also matches
  // on a bare `?` but has a differently-shaped fix (`?:`, not `if`/`else`).
  private val NullishCoalescing = """\A\?\?""".r

  def classify(
    found: String,
    expected: String,
    context: String,
    sourceLine: String
  ): Option[SyntaxHint] = {
    lazy val controlFlowHint = ControlFlowSyntaxHints.classify(found, sourceLine)

    found match {
      case "<:" =>
        hint("error.parsing.hint.old_conforms")
      case "const" =>
        hint("error.parsing.hint.js_style_const")
      case "$" =>
        hint("error.parsing.hint.dollar_sigil")
      case "instanceof" if InstanceofExpression.findFirstMatchIn(sourceLine).isDefined =>
        val matched = InstanceofExpression.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.instanceof_not_supported", matched.group(1), matched.group(2))
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
      case _ if JavaStyleGenericConstructorCall.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleGenericConstructorCall.findFirstMatchIn(sourceLine).get
        val className = matched.group(1)
        val typeArgs = matched.group(2).trim
        if (typeArgs.isEmpty)
          hint("error.parsing.hint.java_style_diamond_operator", className)
        else
          hint("error.parsing.hint.java_style_generic_constructor_call", className, typeArgs)
      // A Python-style `class Foo:` header also lands here (the parser wants
      // `extends`/`{` right where the trailing `:` sits), but it's a missing-brace
      // mistake, not the old colon-extends syntax -- keep it ahead of that case.
      case ":" if expected.contains("extends") && PythonStyleColonBlockClassHeader.findFirstMatchIn(sourceLine).isDefined =>
        val matched = PythonStyleColonBlockClassHeader.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.python_style_colon_block", "class", matched.group(1).trim)
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
      // Keep this family before declarations and the generic missing-block fallback.
      case _ if controlFlowHint.isDefined =>
        controlFlowHint
      case _ if LeadingLetDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.js_style_let")
      case _ if RustStyleMutDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = RustStyleMutDeclaration.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.rust_style_mut", matched.group(1), matched.group(2))
      case _ if DataClassDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = DataClassDeclaration.findFirstMatchIn(sourceLine).get
        val params = matched.group(2).split(",").map { p =>
          ValVarParamPrefix.replaceFirstIn(p.trim, "")
        }.mkString(", ")
        hint("error.parsing.hint.data_class_declaration", matched.group(1), params)
      case _ if PythonStyleLambdaExpression.findFirstMatchIn(sourceLine).isDefined =>
        val matched = PythonStyleLambdaExpression.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.python_style_lambda", matched.group(1).trim, matched.group(2).trim)
      case _ if RustStyleStructDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = RustStyleStructDeclaration.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.struct_declaration", matched.group(1))
      case _ if KotlinScalaStyleObjectDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = KotlinScalaStyleObjectDeclaration.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.object_declaration", matched.group(1))
      case _ if GoStyleShortVarDecl.findFirstMatchIn(sourceLine).isDefined =>
        val matched = GoStyleShortVarDecl.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.go_style_short_var_decl", matched.group(1), matched.group(2).trim)
      case _ if UsingResourceStatement.findFirstMatchIn(sourceLine).isDefined =>
        val matched = UsingResourceStatement.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.using_resource_statement", matched.group(1), matched.group(2).trim)
      case _ if CSharpStyleUsingImport.findFirstMatchIn(sourceLine).isDefined =>
        val matched = CSharpStyleUsingImport.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.csharp_style_using_import", matched.group(1))
      case _ if PythonStyleFromImport.findFirstMatchIn(sourceLine).isDefined =>
        val matched = PythonStyleFromImport.findFirstMatchIn(sourceLine).get
        val module = matched.group(1)
        val names = matched.group(2)
        val imports = names.split(",").map(_.trim).filter(_.nonEmpty).map { n =>
          if (n == "*") s"$module.*" else s"$module.$n"
        }.mkString("; ")
        hint("error.parsing.hint.python_style_from_import", imports, module, names)
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
      // Try the whole-clause-wrapped form first: ForEachDestructureMistake's optional
      // parens around the vars alone would otherwise also match it, but leak the
      // wrapper's closing paren into the collection capture (see its comment above).
      case _ if ForEachDestructureWrappedMistake.findFirstMatchIn(sourceLine).isDefined =>
        val matched = ForEachDestructureWrappedMistake.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.for_each_destructure", matched.group(1), matched.group(2).trim)
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
      case "constructor" if expected.contains("\"def\"") && JsStyleConstructorDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.js_style_constructor")
      case _ if (expected == "\":\"" || expected.contains("\"def\"")) && JavaStyleConstructor.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.java_style_constructor")
      case (")" | ",") if expected == "\":\"" && MissingParameterType.findFirstMatchIn(sourceLine).isDefined =>
        val matched = MissingParameterType.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.missing_parameter_type", matched.group(1))
      case _ if JavaStyleFinalLocalDeclaration.findFirstMatchIn(sourceLine).isDefined =>
        val matched = JavaStyleFinalLocalDeclaration.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.java_style_final_local", matched.group(1), matched.group(2))
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
      case ("\n" | "\r\n" | "\r") if DanglingReturnTypeColon.findFirstMatchIn(sourceLine).isDefined =>
        val matched = DanglingReturnTypeColon.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.dangling_return_type_colon", matched.group(1))
      case "?" if NullishCoalescing.findFirstMatchIn(context).isDefined =>
        hint("error.parsing.hint.nullish_coalescing")
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
      case _ if expected.contains("{") && NotOperatorCondition.findFirstMatchIn(sourceLine).isDefined =>
        hint("error.parsing.hint.not_operator")
      case _ if expected.contains("{") && TypeofCondition.findFirstMatchIn(sourceLine).isDefined =>
        val matched = TypeofCondition.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.typeof_not_supported", matched.group(1))
      // A Rust-Option-style `if let Some(x) = ...` pattern also matches the
      // plain-identifier alternative below it, so keep this ahead of the
      // generic block-expected fallback.
      case _ if expected.contains("{") && IfWhileLetBinding.findFirstMatchIn(sourceLine).isDefined =>
        val matched = IfWhileLetBinding.findFirstMatchIn(sourceLine).get
        val name = Option(matched.group(2)).getOrElse(matched.group(3))
        hint("error.parsing.hint.if_let_binding", matched.group(1), name, matched.group(4).trim)
      // A Python-style `if cond:` / `while cond:` header also matches the generic
      // block-expected fallback below, so keep this ahead of it.
      case ":" if expected.contains("{") && PythonStyleColonBlockHeader.findFirstMatchIn(sourceLine).isDefined =>
        val matched = PythonStyleColonBlockHeader.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.python_style_colon_block", matched.group(1), matched.group(2).trim)
      case _ if expected.contains("{") && !Set(";", "<EOL>", "<EOF>").exists(expected.contains) =>
        hint("error.parsing.hint.block_expected")
      // A Swift-style `guard let x = expr else { ... }` early exit reads as a bare
      // `guard` statement followed by a stray `let`, which also matches the generic
      // missing-call-parens fallback below -- keep this ahead of it.
      case _
          if expected.contains("<EOL>") && expected.contains(";") && expected.contains("<EOF>") &&
            GuardLetElseBinding.findFirstMatchIn(sourceLine).isDefined =>
        val matched = GuardLetElseBinding.findFirstMatchIn(sourceLine).get
        val name = Option(matched.group(1)).getOrElse(matched.group(2))
        hint("error.parsing.hint.guard_let_else", name, matched.group(3).trim)
      // A Swift-style bare `guard <condition> else { ... }` early exit (no
      // `let` binding) reads the same way -- keep it ahead of the generic
      // missing-call-parens fallback below, same as the `let` form above.
      case _
          if expected.contains("<EOL>") && expected.contains(";") && expected.contains("<EOF>") &&
            GuardElseBinding.findFirstMatchIn(sourceLine).isDefined =>
        val matched = GuardElseBinding.findFirstMatchIn(sourceLine).get
        hint("error.parsing.hint.guard_else", matched.group(1).trim)
      // A complete statement was expected right where a second bare token follows its
      // leading identifier with nothing in between -- most often a call whose arguments
      // were written without parentheses (a Python 2 `print "x"` or Ruby `puts x` habit).
      case _
          if expected.contains("<EOL>") && expected.contains(";") && expected.contains("<EOF>") &&
            BareAdjacentTokens.findFirstMatchIn(sourceLine).exists(m => !ReservedWords.contains(m.group(1))) =>
        val name = BareAdjacentTokens.findFirstMatchIn(sourceLine).get.group(1)
        hint("error.parsing.hint.missing_call_parens", name)
      case _ =>
        None
    }
  }

  private def hint(messageKey: String, arguments: String*): Option[SyntaxHint] =
    Some(SyntaxHint(messageKey, arguments))
}
