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
 * Data-driven semantic error reporter.
 *
 * Each error type is associated with an ErrorDef that specifies:
 * - The message key for i18n lookup
 * - Extractors that convert the items array to format arguments
 *
 * @author Kota Mizushima
 */
class SemanticErrorReporter(threshold: Int) {
  private val problems = Buffer[CompileError]()
  private var sourceFile: String = null
  private var errorCount: Int = 0
  private var currentError: SemanticError = null

  // ========== Type extractors ==========

  private def typeName(item: AnyRef): String = item.asInstanceOf[TypedAST.Type].name
  private def classTypeName(item: AnyRef): String = item.asInstanceOf[TypedAST.ClassType].name
  private def objectTypeName(item: AnyRef): String = item.asInstanceOf[TypedAST.ObjectType].name
  private def asString(item: AnyRef): String = item.asInstanceOf[String]
  private def asInt(item: AnyRef): String = item.asInstanceOf[Int].toString
  private def typeNames(types: Array[TypedAST.Type]): String = {
    if (types.isEmpty) "" else types.map(_.name).mkString(", ")
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
    problems.append(new CompileError(sourceFile, position, message, errorCode))
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
    SemanticError.CANNOT_RETURN_VALUE -> ErrorDef(
      "error.semantic.cannotReturnValue",
      Seq()
    ),
    SemanticError.IS_NOT_BOXABLE_TYPE -> ErrorDef(
      "error.semantic.isNotBoxableType",
      Seq(items => typeName(items(0)))
    ),

    // Resolution errors
    SemanticError.CLASS_NOT_FOUND -> ErrorDef(
      "error.semantic.classNotFound",
      Seq(items => asString(items(0)))
    ),
    SemanticError.FIELD_NOT_FOUND -> ErrorDef(
      "error.semantic.fieldNotFound",
      Seq(items => typeName(items(0)), items => asString(items(1)))
    ),
    SemanticError.METHOD_NOT_FOUND -> ErrorDef(
      "error.semantic.methodNotFound",
      Seq(items => typeName(items(0)), items => asString(items(1)), items => typeNames(asTypeArray(items(2))))
    ),
    SemanticError.CONSTRUCTOR_NOT_FOUND -> ErrorDef(
      "error.semantic.constructorNotFound",
      Seq(items => typeName(items(0)), items => typeNames(asTypeArray(items(1))))
    ),
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
      "error.semantic.duplicatedGlobalVariable",
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
    SemanticError.CYCLIC_DELEGATION -> ErrorDef(
      "error.semantic.cyclicDelegation",
      Seq()
    ),
    SemanticError.ILLEGAL_INHERITANCE -> ErrorDef(
      "error.semantic.illegalInheritance",
      Seq(items => asString(items(0)))
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

    // Generic type errors
    SemanticError.TYPE_NOT_GENERIC -> ErrorDef(
      "error.semantic.typeNotGeneric",
      Seq(items => asString(items(0)))
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
    SemanticError.UNKNOWN_PARAMETER_NAME -> ErrorDef(
      "error.semantic.unknownParameterName",
      Seq(items => asString(items(0)))
    ),
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

    // Other errors
    SemanticError.UNIMPLEMENTED_FEATURE -> ErrorDef(
      "error.semantic.unimplementedFeature",
      Seq()
    )
  )

  // ========== Special case handlers ==========

  /**
   * Handles VARIABLE_NOT_FOUND with optional suggestions.
   */
  private def reportVariableNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val name = asString(items(0))
    val baseMessage = format(message("error.semantic.variableNotFound"), Seq(name))

    val suggestion = if (items.length > 1) {
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
    val candidates = targetType match
      case obj: TypedAST.ObjectType => obj.methods.map(_.name).distinct.toSeq
      case _ => Seq.empty
    val suggestion = toolbox.Suggestions.formatSuggestion(name, candidates)
    problem(position, appendSuggestion(baseMessage, suggestion))
  }

  private def reportFieldNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val targetType = items(0).asInstanceOf[TypedAST.Type]
    val name = asString(items(1))
    val baseMessage = format(message("error.semantic.fieldNotFound"), Seq(typeName(targetType), name))
    val candidates = targetType match
      case obj: TypedAST.ObjectType => obj.fields.map(_.name).distinct.toSeq
      case _ => Seq.empty
    val suggestion = toolbox.Suggestions.formatSuggestion(name, candidates)
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
   */
  private def reportNonExhaustivePatternMatch(position: Location, items: Array[AnyRef]): Unit = {
    val sealedType = items(0).asInstanceOf[TypedAST.Type]
    val missingTypes = items(1).asInstanceOf[Array[TypedAST.Type]]
    val missingNames = missingTypes.map(_.name).mkString(", ")
    problem(position, format(message("error.semantic.nonExhaustivePatternMatch"),
      Seq(sealedType.name, missingNames)))
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
      case SemanticError.AMBIGUOUS_METHOD =>
        reportAmbiguousMethod(position, items)
      case SemanticError.AMBIGUOUS_CONSTRUCTOR =>
        reportAmbiguousConstructor(position, items)
      case SemanticError.NON_EXHAUSTIVE_PATTERN_MATCH =>
        reportNonExhaustivePatternMatch(position, items)

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

  def setSourceFile(sourceFile: String): Unit = {
    this.sourceFile = sourceFile
  }
}
