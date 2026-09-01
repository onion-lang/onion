package onion.compiler

/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */

import java.text.MessageFormat
import scala.collection.mutable.Buffer
import onion.compiler.toolbox.{Message, Systems}
import onion.compiler.exceptions.CompilationException

/**
 * Semantic Error Reporter - Central Error Collection and Formatting
 *
 * This class collects and formats semantic errors during type checking.
 * It provides:
 *   - Data-driven error message formatting with i18n support
 *   - Compilation context tracking (class/method being compiled)
 *   - "Did you mean?" suggestions for common typos
 *   - Threshold-based early termination when too many errors occur
 *
 * == Error Reporting Architecture ==
 *
 * The reporter uses a data-driven approach for error formatting:
 *
 * '''1. ErrorDef''': Maps error types to message keys and argument extractors
 *
 * '''2. Special Handlers''': Some errors (METHOD_NOT_FOUND, CLASS_NOT_FOUND, etc.)
 * have custom handlers that provide additional suggestions
 *
 * '''3. Message Formatting''': Uses `java.text.MessageFormat` with i18n properties
 *
 * == Suggestion System ==
 *
 * For resolution errors, the reporter can suggest similar names:
 * {{{
 * error: variable 'naem' is not found
 *   did you mean: name
 * }}}
 *
 * Currently supports suggestions for:
 *   - VARIABLE_NOT_FOUND
 *   - METHOD_NOT_FOUND
 *   - FIELD_NOT_FOUND
 *   - CLASS_NOT_FOUND
 *   - CONSTRUCTOR_NOT_FOUND
 *   - UNKNOWN_PARAMETER_NAME
 *   - LABEL_NOT_FOUND
 *   - OVERRIDE_TARGET_NOT_FOUND
 *
 * == Compilation Context ==
 *
 * The reporter tracks the current compilation context (class, method, phase)
 * to provide more helpful error messages:
 * {{{
 * error: type mismatch (in class Foo, method bar)
 * }}}
 *
 * == Usage ==
 *
 * {{{
 * val reporter = new SemanticErrorReporter(maxErrors = 100)
 * reporter.setSourceFile("MyClass.on")
 * reporter.enterClass("MyClass")
 * reporter.enterMethod("myMethod")
 *
 * // Report an error
 * reporter.report(INCOMPATIBLE_TYPE, location, expectedType, actualType)
 *
 * // Get all collected errors
 * val errors = reporter.getProblems
 * }}}
 *
 * @param threshold Maximum number of errors before throwing CompilationException
 *
 * @see [[SemanticError]] for error type definitions
 * @see [[CompileError]] for error representation
 * @see [[CompilationContext]] for context tracking
 *
 * @author Kota Mizushima
 */
class SemanticErrorReporter(threshold: Int) {
  private val problems = Buffer[CompileError]()
  private var sourceFile: String = null
  private var errorCount: Int = 0
  private var currentError: SemanticError = null
  private var context: CompilationContext = CompilationContext.empty

  // ========== Type extractors ==========

  private def typeName(item: AnyRef): String =
    if (item == null) "<unknown>" else onion.compiler.toolbox.TypeFormatting.sourceForm(item.asInstanceOf[TypedAST.Type])
  private def classTypeName(item: AnyRef): String =
    if (item == null) "<unknown>" else onion.compiler.toolbox.TypeFormatting.sourceForm(item.asInstanceOf[TypedAST.ClassType])
  private def objectTypeName(item: AnyRef): String =
    if (item == null) "<unknown>" else onion.compiler.toolbox.TypeFormatting.sourceForm(item.asInstanceOf[TypedAST.ObjectType])
  private def asString(item: AnyRef): String = item.asInstanceOf[String]
  // English indefinite article for a noun starting with a vowel sound ("enum" -> "an
  // enum") vs. one that doesn't ("record" -> "a record"). Only used where the same
  // message key must grammatically agree with more than one substituted noun -- a fixed
  // noun's article belongs directly in the .properties text instead, as everywhere else.
  private def indefiniteArticled(noun: String): String = {
    val article = noun.headOption.map(_.toLower) match {
      case Some(c) if "aeiou".contains(c) => "an"
      case _ => "a"
    }
    s"$article $noun"
  }
  private def asInt(item: AnyRef): String = item.asInstanceOf[Int].toString
  private def typeNames(types: Array[TypedAST.Type]): String = {
    if (types.isEmpty) "" else types.map(onion.compiler.toolbox.TypeFormatting.sourceForm).mkString(", ")
  }
  private def asTypeArray(item: AnyRef): Array[TypedAST.Type] = item.asInstanceOf[Array[TypedAST.Type]]

  // ========== Message formatting ==========

  private def message(property: String): String = Message(property)

  private def format(template: String, args: Seq[String]): String = {
    if (args.isEmpty) template
    else MessageFormat.format(template, args.asInstanceOf[Seq[AnyRef]]: _*)
  }

  private def appendSuggestion(baseMessage: String, suggestion: Option[String]): String =
    suggestion match {
      case Some(text) => s"$baseMessage${Systems.lineSeparator}  $text"
      case None => baseMessage
    }

  private def problem(position: Location, message: String): Unit = {
    val errorCode = Option(currentError).map(_.errorCode)
    val ctx = if (context == CompilationContext.empty) None else Some(context)
    // Closure bodies are typed more than once (return-type inference,
    // bidirectional trials); drop exact duplicates so each problem is
    // reported a single time
    val duplicate = problems.exists(p =>
      p.sourceFile == sourceFile && p.location == position && p.message == message
    )
    if (!duplicate) {
      problems.append(CompileError(sourceFile, position, message, errorCode, ctx))
    }
  }

  // ========== Error definitions ==========

  /**
   * Error definition with message key and argument extractors.
   * Each extractor function takes the items array and returns a format argument.
   */
  private case class ErrorDef(
    messageKey: String,
    extractors: Seq[Array[AnyRef] => String]
  )

  /**
   * Error definitions for standard errors.
   * Special cases (with suggestions, complex formatting) are handled separately.
   */
  private val errorDefs: Map[SemanticError, ErrorDef] = Map(
    // Type errors
    SemanticError.ILLEGAL_METHOD_CALL -> ErrorDef(
      "error.semantic.illegalMethodCall",
      Seq(items => classTypeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.INCOMPATIBLE_TYPE -> ErrorDef(
      "error.semantic.incompatibleType",
      Seq(items => typeName(items(0)), items => typeName(items(1)))
    ),
    SemanticError.INCOMPATIBLE_OPERAND_TYPE -> ErrorDef(
      "error.semantic.incompatibleOperandType",
      Seq(items => asString(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.LVALUE_REQUIRED -> ErrorDef(
      "error.semantic.lValueRequired",
      Seq()
    ),
    SemanticError.CANNOT_ASSIGN_TO_VAL -> ErrorDef(
      "error.semantic.cannotAssignToVal",
      Seq(items => asString(items(0)))
    ),
    SemanticError.VAL_REQUIRES_INITIALIZER -> ErrorDef(
      "error.semantic.valRequiresInitializer",
      Seq(items => asString(items(0)))
    ),
    SemanticError.CANNOT_RETURN_VALUE -> ErrorDef(
      "error.semantic.cannotReturnValue",
      Seq()
    ),
    SemanticError.IS_NOT_BOXABLE_TYPE -> ErrorDef(
      "error.semantic.isNotBoxableType",
      Seq(items => typeName(items(0)))
    ),

    // Resolution errors - CLASS_NOT_FOUND handled specially with suggestions
    SemanticError.FIELD_NOT_FOUND -> ErrorDef(
      "error.semantic.fieldNotFound",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.METHOD_NOT_FOUND -> ErrorDef(
      "error.semantic.methodNotFound",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    // CONSTRUCTOR_NOT_FOUND handled specially with available constructors
    SemanticError.CANNOT_CALL_METHOD_ON_PRIMITIVE -> ErrorDef(
      "error.semantic.cannotCallMethodOnPrimitive",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.INVALID_METHOD_CALL_TARGET -> ErrorDef(
      "error.semantic.invalidMethodCallTarget",
      Seq(items => typeName(items(0)))
    ),

    // Duplication errors
    SemanticError.DUPLICATE_LOCAL_VARIABLE -> ErrorDef(
      "error.semantic.duplicatedVariable",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_CLASS -> ErrorDef(
      "error.semantic.duplicatedClass",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_FIELD -> ErrorDef(
      "error.semantic.duplicatedField",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.DUPLICATE_METHOD -> ErrorDef(
      "error.semantic.duplicatedMethod",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    SemanticError.DUPLICATE_GLOBAL_VARIABLE -> ErrorDef(
      "error.semantic.duplicatedGlobalVariable",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_FUNCTION -> ErrorDef(
      "error.semantic.duplicatedFunction",
      Seq(items => asString(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.DUPLICATE_CONSTRUCTOR -> ErrorDef(
      "error.semantic.duplicatedConstructor",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.DUPLICATE_TYPE_PARAMETER -> ErrorDef(
      "error.semantic.duplicatedTypeParameter",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_GENERATED_METHOD -> ErrorDef(
      "error.semantic.duplicateGeneratedMethod",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    SemanticError.DUPLICATE_EXTENSION_METHOD -> ErrorDef(
      "error.semantic.duplicateExtensionMethod",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),

    // Access errors
    SemanticError.METHOD_NOT_ACCESSIBLE -> ErrorDef(
      "error.semantic.methodNotAccessible",
      Seq(
        items => objectTypeName(items(0)),
        items => asString(items(1)),
        items => typeNames(asTypeArray(items(2))),
        items => classTypeName(items(3))
      )
    ),
    SemanticError.FIELD_NOT_ACCESSIBLE -> ErrorDef(
      "error.semantic.fieldNotAccessible",
      Seq(items => classTypeName(items(0)), items => asString(items(1)), items => classTypeName(items(2)))
    ),
    SemanticError.CLASS_NOT_ACCESSIBLE -> ErrorDef(
      "error.semantic.classNotAccessible",
      Seq(items => classTypeName(items(0)), items => classTypeName(items(1)))
    ),

    // Inheritance errors
    SemanticError.CYCLIC_INHERITANCE -> ErrorDef(
      "error.semantic.cyclicInheritance",
      Seq(items => asString(items(0)))
    ),
    SemanticError.ILLEGAL_INHERITANCE -> ErrorDef(
      "error.semantic.illegalInheritance",
      Seq(items => asString(items(0)), items => message(s"error.semantic.illegalInheritanceReason.${asString(items(1))}"))
    ),
    SemanticError.INTERFACE_REQUIRED -> ErrorDef(
      "error.semantic.interfaceRequired",
      Seq(items => typeName(items(0)))
    ),
    SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD -> ErrorDef(
      "error.semantic.unimplementedAbstractMethod",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.ABSTRACT_CLASS_INSTANTIATION -> ErrorDef(
      "error.semantic.abstractClassInstantiation",
      Seq(items => typeName(items(0)))
    ),
    SemanticError.FINAL_METHOD_OVERRIDE -> ErrorDef(
      "error.semantic.finalMethodOverride",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    // OVERRIDE_TARGET_NOT_FOUND handled specially with suggestions

    // Generic type errors
    SemanticError.TYPE_NOT_GENERIC -> ErrorDef(
      "error.semantic.typeNotGeneric",
      Seq(items => asString(items(0)))
    ),
    SemanticError.RAW_TYPE_NOT_ALLOWED -> ErrorDef(
      "error.semantic.rawTypeNotAllowed",
      Seq(items => asString(items(0)))
    ),
    SemanticError.MISSING_RETURN -> ErrorDef(
      "error.semantic.missingReturn",
      Seq(items => asString(items(0)), items => typeName(items(1)))
    ),
    SemanticError.TYPE_ARGUMENT_ARITY_MISMATCH -> ErrorDef(
      "error.semantic.typeArgumentArityMismatch",
      Seq(items => asString(items(0)), items => items(1).toString, items => items(2).toString)
    ),
    SemanticError.TYPE_ARGUMENT_MUST_BE_REFERENCE -> ErrorDef(
      "error.semantic.typeArgumentMustBeReference",
      Seq(items => asString(items(0)))
    ),
    SemanticError.METHOD_NOT_GENERIC -> ErrorDef(
      "error.semantic.methodNotGeneric",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.METHOD_TYPE_ARGUMENT_ARITY_MISMATCH -> ErrorDef(
      "error.semantic.methodTypeArgumentArityMismatch",
      Seq(items => asString(items(0)), items => asString(items(1)), items => items(2).toString, items => items(3).toString)
    ),
    SemanticError.ERASURE_SIGNATURE_COLLISION -> ErrorDef(
      "error.semantic.erasureSignatureCollision",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),

    // Pattern matching errors
    SemanticError.DUPLICATE_ARGUMENT -> ErrorDef(
      "error.semantic.duplicateArgument",
      Seq(items => asString(items(0)))
    ),
    SemanticError.POSITIONAL_AFTER_NAMED -> ErrorDef(
      "error.semantic.positionalAfterNamed",
      Seq()
    ),
    SemanticError.WRONG_BINDING_COUNT -> ErrorDef(
      "error.semantic.wrongBindingCount",
      Seq(items => asString(items(2)), items => asInt(items(0)), items => asInt(items(1)))
    ),
    SemanticError.NOT_A_RECORD_TYPE -> ErrorDef(
      "error.semantic.notARecordType",
      Seq(items => asString(items(0)))
    ),

    SemanticError.BREAK_OUTSIDE_LOOP -> ErrorDef(
      "error.semantic.breakOutsideLoop",
      Seq()
    ),
    SemanticError.CONTINUE_OUTSIDE_LOOP -> ErrorDef(
      "error.semantic.continueOutsideLoop",
      Seq()
    ),
    SemanticError.CURRENT_INSTANCE_NOT_AVAILABLE -> ErrorDef(
      "error.semantic.currentInstanceNotAvailable",
      Seq()
    ),
    SemanticError.RETURN_TYPE_REQUIRED -> ErrorDef(
      "error.semantic.returnTypeRequired",
      Seq(items => asString(items(0)))
    ),
    SemanticError.LAMBDA_PARAM_TYPE_REQUIRED -> ErrorDef(
      "error.semantic.lambdaParamTypeRequired",
      Seq(items => asString(items(0)))
    ),
    SemanticError.FUNCTION_BODY_REQUIRED -> ErrorDef(
      "error.semantic.functionBodyRequired",
      Seq(items => asString(items(0)))
    ),
    SemanticError.TYPE_PARAMETER_MAY_BE_NULL -> ErrorDef(
      "error.semantic.typeParameterMayBeNull",
      Seq(items => asString(items(0)))
    ),
    SemanticError.NULLABLE_MEMBER_ACCESS -> ErrorDef(
      "error.semantic.nullableMemberAccess",
      Seq(items => asString(items(0)))
    ),
    SemanticError.STATIC_CALL_ON_INSTANCE -> ErrorDef(
      "error.semantic.staticCallOnInstance",
      Seq(items => asString(items(0)))
    ),
    SemanticError.CLASS_USED_AS_VALUE -> ErrorDef(
      "error.semantic.classUsedAsValue",
      Seq(items => asString(items(0)))
    ),
    SemanticError.ABSTRACT_METHOD_WITH_BODY -> ErrorDef(
      "error.semantic.abstractMethodWithBody",
      Seq(items => asString(items(0)))
    ),
    SemanticError.MAP_NOT_DIRECTLY_ITERABLE -> ErrorDef(
      "error.semantic.mapNotDirectlyIterable",
      Seq(items => typeName(items(0)))
    ),
    // LABEL_NOT_FOUND handled specially with suggestions
    SemanticError.REGEX_PATTERN_INVALID -> ErrorDef(
      "error.semantic.regexPatternInvalid",
      Seq(items => asString(items(0)))
    ),
    SemanticError.REGEX_GROUP_MISMATCH -> ErrorDef(
      "error.semantic.regexGroupMismatch",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.RECORD_FROM_COMPONENT_UNSUPPORTED -> ErrorDef(
      "error.semantic.recordFromComponentUnsupported",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.RECORD_DERIVE_COMPONENT_UNSUPPORTED -> ErrorDef(
      "error.semantic.recordDeriveComponentUnsupported",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.RECORD_DERIVE_UNKNOWN_MARKER -> ErrorDef(
      "error.semantic.recordDeriveUnknownMarker",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.SHAPE_FORMAT_UNKNOWN -> ErrorDef(
      "error.semantic.shapeFormatUnknown",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.TOOL_UNDECLARED_EFFECT -> ErrorDef(
      "error.semantic.toolUndeclaredEffect",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.TOOL_UNUSED_CAPABILITY -> ErrorDef(
      "error.semantic.toolUnusedCapability",
      Seq(items => asString(items(0)), items => asString(items(1)))
    ),
    SemanticError.TOOL_BAD_CAPABILITY -> ErrorDef(
      "error.semantic.toolBadCapability",
      Seq(items => asString(items(0)), items => asString(items(1)), items => asString(items(2)))
    ),
    SemanticError.DUPLICATE_TOOL_NAME -> ErrorDef(
      "error.semantic.duplicateToolName",
      Seq(items => asString(items(0)))
    ),
    SemanticError.UNREACHABLE_CATCH_CLAUSE -> ErrorDef(
      "error.semantic.unreachableCatchClause",
      Seq(items => typeName(items(0)), items => typeName(items(1)))
    ),
    SemanticError.SHAPE_INSTANCE_WITHOUT_LAW -> ErrorDef(
      "error.semantic.shapeInstanceWithoutLaw",
      Seq(items => asString(items(0)))
    ),
    SemanticError.STATIC_METHOD_WITHOUT_BODY -> ErrorDef(
      "error.semantic.staticMethodWithoutBody",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_RECORD_COMPONENT -> ErrorDef(
      "error.semantic.duplicateRecordComponent",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    // {0} = the class, {1} = the primary constructor's parameter types, so the fix is
    // copy-pasteable: `def this(...) : this(<{1}>) { ... }`.
    SemanticError.SECONDARY_CONSTRUCTOR_MUST_DELEGATE -> ErrorDef(
      "error.semantic.secondaryConstructorMustDelegate",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    SemanticError.CONSTRUCTOR_DELEGATION_CYCLE -> ErrorDef(
      "error.semantic.constructorDelegationCycle",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
    // {2} is derived from {0} (not a separate call-site argument): the English
    // template needs the article to agree with whichever noun ("record" or "enum")
    // was passed, while the Japanese template uses the bare {0} directly (no
    // articles in Japanese), so both are extracted from the same source value.
    SemanticError.CONSTRUCTOR_IN_RECORD_OR_ENUM -> ErrorDef(
      "error.semantic.constructorInRecordOrEnum",
      Seq(items => asString(items(0)), items => typeName(items(1)), items => indefiniteArticled(asString(items(0))))
    ),
    SemanticError.THIS_BEFORE_CONSTRUCTOR_DELEGATION -> ErrorDef(
      "error.semantic.thisBeforeConstructorDelegation",
      Seq(items => typeName(items(0)))
    ),
    // Reported through the normal reporter path (NameResolution / TypingHeaderPass)
    // but never wired here, so both rendered as "Unknown error: <NAME>" (found while
    // sweeping E-code coverage for 0.10.1).
    SemanticError.CYCLIC_TYPE_ALIAS -> ErrorDef(
      "error.semantic.cyclicTypeAlias",
      Seq(items => asString(items(0)))
    ),
    SemanticError.DUPLICATE_TYPE_ALIAS -> ErrorDef(
      "error.semantic.duplicateTypeAlias",
      Seq(items => asString(items(0)))
    )
  )

  // ========== Special case handlers ==========

  // A JS/TS-style backtick template literal (`` `Hello ${name}` ``) isn't a string in
  // Onion -- backticks only escape a reserved word into an identifier, so the whole
  // `${...}` text becomes the literal name of a local variable reference, and the
  // mistake surfaces as a VARIABLE_NOT_FOUND whose message repeats the entire template
  // text back as if it were an identifier. Detect that shape in the unresolved name
  // and point at Onion's actual interpolation syntax instead of a name-similarity guess.
  private val TemplateLiteralInName = """\$\{.*\}""".r

  // A JS-style `console.log(...)`/`console.error(...)`/`console.warn(...)` call has no
  // `console` object in Onion, so `console` parses as a bare identifier reference and
  // the mistake surfaces as a VARIABLE_NOT_FOUND on exactly that name, with nothing
  // connecting it back to Onion's actual output API. Detect that exact identifier and
  // point at `IO::println` instead of a name-similarity guess.
  private val ConsoleObjectName = "console"

  /**
   * Handles VARIABLE_NOT_FOUND with optional suggestions.
   */
  private def reportVariableNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.variableNotFound"), Seq(name))

    // A third item is an explicit qualified-form hint (e.g. "this.x" / "C::x")
    // for a name that is actually a field; prefer it over name-similarity.
    val suggestion =
      if (TemplateLiteralInName.findFirstIn(name).isDefined) {
        Some(message("suggestion.jsTemplateLiteral"))
      } else if (name == ConsoleObjectName) {
        Some(message("suggestion.jsConsoleLog"))
      } else if (items.length > 2 && items(2) != null) {
        Some(format(message("suggestion.didYouMean"), Seq(asString(items(2)))))
      } else if (items.length > 1) {
        val candidates = items(1).asInstanceOf[Array[String]]
        toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
      } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  private def reportMethodNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val targetType = items(0).asInstanceOf[TypedAST.Type]
    val name = asString(items(1))
    val args = typeNames(asTypeArray(items(2)))
    val baseMessage = format(message("error.semantic.methodNotFound"), Seq(typeName(targetType), name, args))
    // A method with that exact name exists: show its signatures instead of
    // a (possibly misleading) name-similarity suggestion
    val sameName = targetType match
      case obj: TypedAST.ObjectType => obj.methods.filter(_.name == name).toSeq
      case _ => Seq.empty
    val suggestion =
      if (sameName.nonEmpty) {
        val signatures = sameName.take(3).map { m =>
          s"${m.name}(${m.arguments.map(typeName).mkString(", ")})"
        }
        Some(format(message("error.suggestion.candidates"), Seq(signatures.mkString(", "))))
      } else if (name == "f" && args == "String") {
        // A Python-style f-string prefix (`f"Hello {name}"`) is not a string in Onion --
        // any bare identifier directly before a string literal desugars to a call (the
        // scheme-literal sugar: `re"..."`/`file"..."`/a user-defined `id"raw"`), so this
        // reads as a call to a function named `f` that nothing defines. Point at Onion's
        // actual interpolation syntax instead of a name-similarity guess.
        Some(message("suggestion.pythonFString"))
      } else if (fieldNamed(targetType, name) || isArrayLengthProperty(targetType, name)) {
        // `p.name()` where `name` is a field, not a method -- a common mix-up
        // with record component accessors (which really are methods). A
        // name-similarity hint would uselessly suggest the same spelling, so
        // point at the parentheses instead. `arr.length()`/`arr.size()` hits
        // the same mix-up, but arrays carry no real `length` field entry for
        // fieldNamed to match (their length is resolved specially, not as a
        // TypedAST field), so it needs its own check.
        Some(format(message("suggestion.fieldNotMethod"), Seq(name)))
      } else {
        val candidates = targetType match
          case obj: TypedAST.ObjectType =>
            (obj.methods.map(_.name) ++ obj.fields.map(_.name)).distinct.toSeq
          case _ => Seq.empty
        toolbox.Suggestions.formatSuggestion(name, candidates)
      }
    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  private def fieldNamed(targetType: TypedAST.Type, name: String): Boolean =
    targetType match
      case obj: TypedAST.ObjectType => obj.fields.exists(_.name == name)
      case _ => false

  private def isArrayLengthProperty(targetType: TypedAST.Type, name: String): Boolean =
    targetType.isArrayType && (name == "length" || name == "size")

  private def reportFieldNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val targetType = items(0).asInstanceOf[TypedAST.Type]
    val name = asString(items(1))
    val baseMessage = format(message("error.semantic.fieldNotFound"), Seq(typeName(targetType), name))
    // A no-argument method is reachable with the same paren-less syntax as a
    // field (`xs.size`), so it is a valid fix for a misremembered field name --
    // `xs.length` on a List should suggest `size` even though List has no
    // fields at all. Without the methods here the candidate list was often
    // empty and the user got no guidance.
    val candidates = targetType match
      case obj: TypedAST.ObjectType =>
        (obj.fields.map(_.name) ++ obj.methods.filter(_.arguments.isEmpty).map(_.name)).distinct.toSeq
      case _ => Seq.empty
    val suggestion = toolbox.Suggestions.formatSuggestion(name, candidates)
    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles LABEL_NOT_FOUND with optional suggestions for similar labels
   * among the enclosing labeled loops.
   */
  private def reportLabelNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.labelNotFound"), Seq(name))

    val suggestion = if (items.length > 1) {
      val candidates = items(1).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles CLASS_NOT_FOUND with optional suggestions for similar class names.
   */
  private def reportClassNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.classNotFound"), Seq(name))

    val suggestion = if (items.length > 1) {
      val candidates = items(1).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles UNKNOWN_PARAMETER_NAME with optional suggestions for similar
   * parameter names.
   */
  private def reportUnknownParameterName(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.unknownParameterName"), Seq(name))

    val suggestion = if (items.length > 1) {
      val candidates = items(1).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles OVERRIDE_TARGET_NOT_FOUND with optional suggestions for similar
   * method names among the base class's/interfaces' overridable methods.
   */
  private def reportOverrideTargetNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val paramDescriptor = asString(items(1))
    val className = asString(items(2))
    val baseMessage = format(message("error.semantic.overrideTargetNotFound"), Seq(name, paramDescriptor, className))

    val suggestion = if (items.length > 3) {
      val candidates = items(3).asInstanceOf[Array[String]]
      toolbox.Suggestions.formatSuggestion(name, candidates.toSeq)
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles CONSTRUCTOR_NOT_FOUND with available constructors display.
   */
  private def reportConstructorNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val typeRef = items(0).asInstanceOf[TypedAST.Type]
    val argTypes = asTypeArray(items(1))
    val baseMessage = format(message("error.semantic.constructorNotFound"), Seq(typeName(typeRef), typeNames(argTypes)))

    val suggestion = if (items.length > 2) {
      val constructors = items(2).asInstanceOf[Array[TypedAST.ConstructorRef]]
      if (constructors.nonEmpty) {
        val ctorSignatures = constructors.map { ctor =>
          s"  ${typeRef.name}(${ctor.getArgs.map(onion.compiler.toolbox.TypeFormatting.sourceForm).mkString(", ")})"
        }
        Some(s"${message("error.suggestion.availableConstructors")}${Systems.lineSeparator}${ctorSignatures.mkString(Systems.lineSeparator)}")
      } else None
    } else None

    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  /**
   * Handles AMBIGUOUS_METHOD with complex item structure.
   */
  private def reportAmbiguousMethod(position: Location, items: Array[AnyRef]): Unit = {
    val item1 = items(0).asInstanceOf[Array[AnyRef]]
    val item2 = items(1).asInstanceOf[Array[AnyRef]]
    val target1 = objectTypeName(item1(0))
    val name1 = asString(item1(1))
    val args1 = typeNames(asTypeArray(item1(2)))
    val target2 = objectTypeName(item2(0))
    val name2 = asString(item2(1))
    val args2 = typeNames(asTypeArray(item2(2)))
    problem(position, format(message("error.semantic.ambiguousMethod"),
      Seq(target1, name1, args1, target2, name2, args2)))
  }

  /**
   * Handles AMBIGUOUS_CONSTRUCTOR with complex item structure.
   */
  private def reportAmbiguousConstructor(position: Location, items: Array[AnyRef]): Unit = {
    val item1 = items(0).asInstanceOf[Array[AnyRef]]
    val item2 = items(1).asInstanceOf[Array[AnyRef]]
    val target1 = objectTypeName(item1(0))
    val args1 = typeNames(asTypeArray(item1(1)))
    val target2 = objectTypeName(item2(0))
    val args2 = typeNames(asTypeArray(item2(1)))
    problem(position, format(message("error.semantic.ambiguousConstructor"),
      Seq(target1, args1, target2, args2)))
  }

  /**
   * Handles NON_EXHAUSTIVE_PATTERN_MATCH with type array formatting.
   * Uses displayName to show generic type arguments in error messages.
   */
  private def reportNonExhaustivePatternMatch(position: Location, items: Array[AnyRef]): Unit = {
    val sealedType = items(0).asInstanceOf[TypedAST.Type]
    // Missing cases are subtypes for sealed hierarchies, constant names for enums
    val missingNames = items(1).asInstanceOf[Array[?]].map {
      case t: TypedAST.Type => onion.compiler.toolbox.TypeFormatting.sourceForm(t)
      case other => String.valueOf(other)
    }.mkString(", ")
    problem(position, format(message("error.semantic.nonExhaustivePatternMatch"),
      Seq(onion.compiler.toolbox.TypeFormatting.sourceForm(sealedType), missingNames)))
  }

  /**
   * Handles INCOMPATIBLE_TYPE with a null-safety hint when a nullable value
   * is used where a non-null type is expected.
   */
  private def reportIncompatibleType(position: Location, items: Array[AnyRef]): Unit = {
    val expected = items(0).asInstanceOf[TypedAST.Type]
    val actual = items(1).asInstanceOf[TypedAST.Type]
    val baseMessage = format(message("error.semantic.incompatibleType"), Seq(typeName(expected), typeName(actual)))
    val hint =
      if (expected != null && actual != null && !expected.isNullable && actual.isNullable) {
        Some(format(message("suggestion.nullableToNonNull"), Seq()))
      } else None
    problem(position, appendSuggestion(baseMessage, hint))
  }

  // ========== Main report method ==========

  def report(error: SemanticError, position: Location, items: Array[AnyRef]): Unit = {
    errorCount += 1
    currentError = error

    error match {
      // Special cases that need custom handling
      case SemanticError.VARIABLE_NOT_FOUND =>
        reportVariableNotFound(position, items)
      case SemanticError.METHOD_NOT_FOUND =>
        reportMethodNotFound(position, items)
      case SemanticError.FIELD_NOT_FOUND =>
        reportFieldNotFound(position, items)
      case SemanticError.LABEL_NOT_FOUND =>
        reportLabelNotFound(position, items)
      case SemanticError.CLASS_NOT_FOUND =>
        reportClassNotFound(position, items)
      case SemanticError.CONSTRUCTOR_NOT_FOUND =>
        reportConstructorNotFound(position, items)
      case SemanticError.UNKNOWN_PARAMETER_NAME =>
        reportUnknownParameterName(position, items)
      case SemanticError.OVERRIDE_TARGET_NOT_FOUND =>
        reportOverrideTargetNotFound(position, items)
      case SemanticError.AMBIGUOUS_METHOD =>
        reportAmbiguousMethod(position, items)
      case SemanticError.AMBIGUOUS_CONSTRUCTOR =>
        reportAmbiguousConstructor(position, items)
      case SemanticError.NON_EXHAUSTIVE_PATTERN_MATCH =>
        reportNonExhaustivePatternMatch(position, items)
      case SemanticError.INCOMPATIBLE_TYPE =>
        reportIncompatibleType(position, items)

      // Data-driven cases
      case _ =>
        errorDefs.get(error) match {
          case Some(defn) =>
            val args = defn.extractors.map(_.apply(items))
            problem(position, format(message(defn.messageKey), args))
          case None =>
            // Fallback for any unmapped error
            problem(position, s"Unknown error: $error")
        }
    }

    if (errorCount >= threshold) {
      throw new CompilationException(problems.toSeq)
    }
  }

  def getProblems: Array[CompileError] = problems.toArray

  /** Number of errors collected so far. Lets callers checkpoint before/after a
   *  speculative typing attempt (e.g. a trailing-closure body) to detect whether
   *  it reported a fresh error, so a redundant outer method-not-found can be
   *  suppressed (issue #316). */
  def problemCount: Int = problems.length

  def setSourceFile(sourceFile: String): Unit = {
    this.sourceFile = sourceFile
  }

  // ========== Context management ==========

  /** Sets the current compilation context */
  def setContext(ctx: CompilationContext): Unit = {
    this.context = ctx
  }

  /** Gets the current compilation context */
  def getContext: CompilationContext = context

  /** Enters a class context */
  def enterClass(className: String): Unit = {
    context = context.withClass(className)
  }

  /** Enters a method context */
  def enterMethod(methodName: String): Unit = {
    context = context.withMethod(methodName)
  }

  /** Leaves the current method context */
  def leaveMethod(): Unit = {
    context = context.clearMethod
  }

  /** Leaves the current class context */
  def leaveClass(): Unit = {
    context = context.clearClass
  }

  /** Sets the compilation phase */
  def setPhase(phase: String): Unit = {
    context = context.withPhase(phase)
  }

  /** Resets the context to empty */
  def resetContext(): Unit = {
    context = CompilationContext.empty
  }
}
