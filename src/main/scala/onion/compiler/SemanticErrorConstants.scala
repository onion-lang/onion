package onion.compiler

/**
 * Created by IntelliJ IDEA.
 * User: Mizushima
 * Date: 11/05/17
 * Time: 0:09
 * To change this template use File | Settings | File Templates.
 */
object SemanticErrorConstants {
  final val INCOMPATIBLE_TYPE: Int = 0
  final val INCOMPATIBLE_OPERAND_TYPE: Int = 1
  final val VARIABLE_NOT_FOUND: Int = 2
  final val CLASS_NOT_FOUND: Int = 3
  final val FIELD_NOT_FOUND: Int = 4
  final val METHOD_NOT_FOUND: Int = 5
  final val AMBIGUOUS_METHOD: Int = 6
  final val DUPLICATE_LOCAL_VARIABLE: Int = 7
  final val DUPLICATE_CLASS: Int = 8
  final val DUPLICATE_FIELD: Int = 9
  final val DUPLICATE_METHOD: Int = 10
  final val DUPLICATE_GLOBAL_VARIABLE: Int = 11
  final val DUPLICATE_FUNCTION: Int = 12
  final val METHOD_NOT_ACCESSIBLE: Int = 13
  final val FIELD_NOT_ACCESSIBLE: Int = 14
  final val CLASS_NOT_ACCESSIBLE: Int = 15
  final val CYCLIC_INHERITANCE: Int = 16
  final val CYCLIC_DELEGATION: Int = 17
  final val ILLEGAL_INHERITANCE: Int = 18
  final val ILLEGAL_METHOD_CALL: Int = 19
  final val CANNOT_RETURN_VALUE: Int = 20
  final val CONSTRUCTOR_NOT_FOUND: Int = 21
  final val AMBIGUOUS_CONSTRUCTOR: Int = 22
  final val INTERFACE_REQUIRED: Int = 23
  final val UNIMPLEMENTED_FEATURE: Int = 24
  final val DUPLICATE_CONSTRUCTOR: Int = 25
  final val DUPLICATE_GENERATED_METHOD: Int = 26
  final val IS_NOT_BOXABLE_TYPE: Int = 27
  final val LVALUE_REQUIRED: Int = 28
}