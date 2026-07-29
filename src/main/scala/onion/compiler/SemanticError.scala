/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2016-, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler

/**
 * Semantic Error Codes for the Onion Compiler
 *
 * This object defines all semantic error types that can be reported during
 * type checking. Each error has a unique code (E0000-E0083) for identification
 * and i18n message lookup.
 *
 * == Error Categories ==
 *
 * '''Type Errors (E0000-E0001)'''
 *   - INCOMPATIBLE_TYPE: Type mismatch in assignment, return, or expression
 *   - INCOMPATIBLE_OPERAND_TYPE: Invalid operand type for operator
 *
 * '''Resolution Errors (E0002-E0006, E0021-E0022)'''
 *   - VARIABLE_NOT_FOUND, CLASS_NOT_FOUND, FIELD_NOT_FOUND, METHOD_NOT_FOUND
 *   - CONSTRUCTOR_NOT_FOUND, AMBIGUOUS_METHOD, AMBIGUOUS_CONSTRUCTOR
 *
 * '''Duplication Errors (E0007-E0012, E0025-E0026, E0029, E0053-E0054)'''
 *   - DUPLICATE_LOCAL_VARIABLE, DUPLICATE_CLASS, DUPLICATE_FIELD
 *   - DUPLICATE_METHOD, DUPLICATE_CONSTRUCTOR, DUPLICATE_TYPE_PARAMETER
 *   - CYCLIC_TYPE_ALIAS, DUPLICATE_TYPE_ALIAS
 *
 * '''Access Errors (E0013-E0015)'''
 *   - METHOD_NOT_ACCESSIBLE, FIELD_NOT_ACCESSIBLE, CLASS_NOT_ACCESSIBLE
 *
 * '''Inheritance Errors (E0016-E0018, E0037-E0039)'''
 *   - CYCLIC_INHERITANCE, ILLEGAL_INHERITANCE, INTERFACE_REQUIRED
 *   - UNIMPLEMENTED_ABSTRACT_METHOD, ABSTRACT_CLASS_INSTANTIATION, FINAL_METHOD_OVERRIDE
 *
 * '''Generic Type Errors (E0030-E0035, E0066)'''
 *   - TYPE_NOT_GENERIC, TYPE_ARGUMENT_ARITY_MISMATCH, TYPE_ARGUMENT_MUST_BE_REFERENCE
 *   - METHOD_NOT_GENERIC, METHOD_TYPE_ARGUMENT_ARITY_MISMATCH, ERASURE_SIGNATURE_COLLISION
 *   - RAW_TYPE_NOT_ALLOWED
 *
 * '''Control Flow Errors (E0048-E0050, E0067)'''
 *   - BREAK_OUTSIDE_LOOP, CONTINUE_OUTSIDE_LOOP, CURRENT_INSTANCE_NOT_AVAILABLE
 *   - MISSING_RETURN
 *
 * '''Pattern Matching Errors (E0042-E0047)'''
 *   - NON_EXHAUSTIVE_PATTERN_MATCH, UNKNOWN_PARAMETER_NAME, DUPLICATE_ARGUMENT
 *   - POSITIONAL_AFTER_NAMED, WRONG_BINDING_COUNT, NOT_A_RECORD_TYPE
 *
 * '''Regex and Label Errors (E0058-E0060)'''
 *   - LABEL_NOT_FOUND, REGEX_PATTERN_INVALID, REGEX_GROUP_MISMATCH
 *
 * '''Record Errors (E0061-E0063)'''
 *   - RECORD_FROM_COMPONENT_UNSUPPORTED, RECORD_DERIVE_COMPONENT_UNSUPPORTED
 *   - RECORD_DERIVE_UNKNOWN_MARKER
 *
 * '''Law and Example Errors (E0064-E0065, E0074-E0075)'''
 *   - LAW_VIOLATION, EXAMPLE_FAILED, LAW_PARAMETER_NOT_GENERATABLE, LAW_CLASS_NOT_LOADABLE
 *
 * '''Shape and Tool Errors (E0076-E0083)'''
 *   - SHAPE_FORMAT_UNKNOWN, TOOL_UNDECLARED_EFFECT, TOOL_UNUSED_CAPABILITY
 *   - TOOL_BAD_CAPABILITY, SHAPE_INSTANCE_WITHOUT_LAW, TOOL_PARAMETER_NOT_CLI_CONVERTIBLE
 *   - DUPLICATE_TOOL_NAME, UNREACHABLE_CATCH_CLAUSE
 *
 * == Error Message Lookup ==
 *
 * Error messages are stored in `errorMessage.properties` (English) and
 * `errorMessage_ja.properties` (Japanese). The message key format is:
 * `error.semantic.<errorName>` (e.g., `error.semantic.incompatibleType`).
 *
 * == Usage ==
 *
 * {{{
 * import onion.compiler.SemanticError._
 *
 * // Report a type error
 * reporter.report(INCOMPATIBLE_TYPE, location, expectedType, actualType)
 * }}}
 *
 * @see [[SemanticErrorReporter]] for error reporting
 * @see [[CompileError]] for error representation
 */
object SemanticError {
  case object INCOMPATIBLE_TYPE extends SemanticError(0)
  case object INCOMPATIBLE_OPERAND_TYPE extends SemanticError(1)
  case object VARIABLE_NOT_FOUND extends SemanticError(2)
  case object CLASS_NOT_FOUND extends SemanticError(3)
  case object FIELD_NOT_FOUND extends SemanticError(4)
  case object METHOD_NOT_FOUND extends SemanticError(5)
  case object AMBIGUOUS_METHOD extends SemanticError(6)
  case object DUPLICATE_LOCAL_VARIABLE extends SemanticError(7)
  case object DUPLICATE_CLASS extends SemanticError(8)
  case object DUPLICATE_FIELD extends SemanticError(9)
  case object DUPLICATE_METHOD extends SemanticError(10)
  case object DUPLICATE_GLOBAL_VARIABLE extends SemanticError(11)
  case object DUPLICATE_FUNCTION extends SemanticError(12)
  case object METHOD_NOT_ACCESSIBLE extends SemanticError(13)
  case object FIELD_NOT_ACCESSIBLE extends SemanticError(14)
  case object CLASS_NOT_ACCESSIBLE extends SemanticError(15)
  case object CYCLIC_INHERITANCE extends SemanticError(16)
  case object ILLEGAL_INHERITANCE extends SemanticError(18)
  case object ILLEGAL_METHOD_CALL extends SemanticError(19)
  case object CANNOT_RETURN_VALUE extends SemanticError(20)
  case object CONSTRUCTOR_NOT_FOUND extends SemanticError(21)
  case object AMBIGUOUS_CONSTRUCTOR extends SemanticError(22)
  case object INTERFACE_REQUIRED extends SemanticError(23)
  case object DUPLICATE_CONSTRUCTOR extends SemanticError(25)
  case object DUPLICATE_GENERATED_METHOD extends SemanticError(26)
  case object IS_NOT_BOXABLE_TYPE extends SemanticError(27)
  case object LVALUE_REQUIRED extends SemanticError(28)
  case object DUPLICATE_TYPE_PARAMETER extends SemanticError(29)
  case object TYPE_NOT_GENERIC extends SemanticError(30)
  case object TYPE_ARGUMENT_ARITY_MISMATCH extends SemanticError(31)
  case object TYPE_ARGUMENT_MUST_BE_REFERENCE extends SemanticError(32)
  case object METHOD_NOT_GENERIC extends SemanticError(33)
  case object METHOD_TYPE_ARGUMENT_ARITY_MISMATCH extends SemanticError(34)
  case object ERASURE_SIGNATURE_COLLISION extends SemanticError(35)
  case object CANNOT_ASSIGN_TO_VAL extends SemanticError(36)
  case object UNIMPLEMENTED_ABSTRACT_METHOD extends SemanticError(37)
  case object ABSTRACT_CLASS_INSTANTIATION extends SemanticError(38)
  case object FINAL_METHOD_OVERRIDE extends SemanticError(39)
  case object CANNOT_CALL_METHOD_ON_PRIMITIVE extends SemanticError(40)
  case object INVALID_METHOD_CALL_TARGET extends SemanticError(41)
  case object NON_EXHAUSTIVE_PATTERN_MATCH extends SemanticError(42)
  case object UNKNOWN_PARAMETER_NAME extends SemanticError(43)
  case object DUPLICATE_ARGUMENT extends SemanticError(44)
  case object POSITIONAL_AFTER_NAMED extends SemanticError(45)
  case object WRONG_BINDING_COUNT extends SemanticError(46)
  case object NOT_A_RECORD_TYPE extends SemanticError(47)
  case object BREAK_OUTSIDE_LOOP extends SemanticError(48)
  case object CONTINUE_OUTSIDE_LOOP extends SemanticError(49)
  case object CURRENT_INSTANCE_NOT_AVAILABLE extends SemanticError(50)
  case object RETURN_TYPE_REQUIRED extends SemanticError(51)
  case object LAMBDA_PARAM_TYPE_REQUIRED extends SemanticError(52)
  case object CYCLIC_TYPE_ALIAS extends SemanticError(53)
  case object DUPLICATE_TYPE_ALIAS extends SemanticError(54)
  case object FUNCTION_BODY_REQUIRED extends SemanticError(55)
  case object TYPE_PARAMETER_MAY_BE_NULL extends SemanticError(57)
  case object LABEL_NOT_FOUND extends SemanticError(58)
  case object REGEX_PATTERN_INVALID extends SemanticError(59)
  case object REGEX_GROUP_MISMATCH extends SemanticError(60)
  case object RECORD_FROM_COMPONENT_UNSUPPORTED extends SemanticError(61)
  case object RECORD_DERIVE_COMPONENT_UNSUPPORTED extends SemanticError(62)
  case object RECORD_DERIVE_UNKNOWN_MARKER extends SemanticError(63)
  case object LAW_VIOLATION extends SemanticError(64)
  case object EXAMPLE_FAILED extends SemanticError(65)
  case object RAW_TYPE_NOT_ALLOWED extends SemanticError(66)
  case object MISSING_RETURN extends SemanticError(67)
  case object OVERRIDE_TARGET_NOT_FOUND extends SemanticError(68)
  case object VAL_REQUIRES_INITIALIZER extends SemanticError(69)
  case object NULLABLE_MEMBER_ACCESS extends SemanticError(70)
  case object STATIC_CALL_ON_INSTANCE extends SemanticError(71)
  case object ABSTRACT_METHOD_WITH_BODY extends SemanticError(72)
  case object MAP_NOT_DIRECTLY_ITERABLE extends SemanticError(73)
  case object LAW_PARAMETER_NOT_GENERATABLE extends SemanticError(74)
  case object LAW_CLASS_NOT_LOADABLE extends SemanticError(75)
  case object SHAPE_FORMAT_UNKNOWN extends SemanticError(76)
  case object TOOL_UNDECLARED_EFFECT extends SemanticError(77)
  case object TOOL_UNUSED_CAPABILITY extends SemanticError(78)
  case object TOOL_BAD_CAPABILITY extends SemanticError(79)
  case object SHAPE_INSTANCE_WITHOUT_LAW extends SemanticError(80)
  case object TOOL_PARAMETER_NOT_CLI_CONVERTIBLE extends SemanticError(81)
  case object DUPLICATE_TOOL_NAME extends SemanticError(82)
  case object UNREACHABLE_CATCH_CLAUSE extends SemanticError(83)
}
sealed abstract class SemanticError(val code: Int) {
  /** Returns the error code in format "E0001" */
  def errorCode: String = f"E${code}%04d"
}
