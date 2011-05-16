package onion.compiler;

/**
* Created by IntelliJ IDEA.
* User: Mizushima
* Date: 11/05/17
* Time: 0:09
* To change this template use File | Settings | File Templates.
*/
public interface SemanticErrorConstants {
  int INCOMPATIBLE_TYPE = 0;
  int INCOMPATIBLE_OPERAND_TYPE = 1;
  int VARIABLE_NOT_FOUND = 2;
  int CLASS_NOT_FOUND = 3;
  int FIELD_NOT_FOUND = 4;
  int METHOD_NOT_FOUND = 5;
  int AMBIGUOUS_METHOD = 6;
  int DUPLICATE_LOCAL_VARIABLE = 7;
  int DUPLICATE_CLASS = 8;
  int DUPLICATE_FIELD = 9;
  int DUPLICATE_METHOD = 10;
  int DUPLICATE_GLOBAL_VARIABLE = 11;
  int DUPLICATE_FUNCTION = 12;
  int METHOD_NOT_ACCESSIBLE = 13;
  int FIELD_NOT_ACCESSIBLE = 14;
  int CLASS_NOT_ACCESSIBLE = 15;
  int CYCLIC_INHERITANCE = 16;
  int CYCLIC_DELEGATION = 17;
  int ILLEGAL_INHERITANCE = 18;
  int ILLEGAL_METHOD_CALL = 19;
  int CANNOT_RETURN_VALUE = 20;
  int CONSTRUCTOR_NOT_FOUND = 21;
  int AMBIGUOUS_CONSTRUCTOR= 22;
  int INTERFACE_REQUIRED = 23;
  int UNIMPLEMENTED_FEATURE = 24;
  int DUPLICATE_CONSTRUCTOR = 25;
  int DUPLICATE_GENERATED_METHOD = 26;
  int IS_NOT_BOXABLE_TYPE = 27;
  int LVALUE_REQUIRED = 28;
}
