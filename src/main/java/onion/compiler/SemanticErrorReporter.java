/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005, Kota Mizushima, All rights reserved.       *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */
package onion.compiler;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import onion.compiler.util.Messages;


/**
 * @author Kota Mizushima
 * Date: 2005/06/23
 */
public class SemanticErrorReporter {
  public interface Constants {
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
  
  private List problems;
  private String sourceFile;
  private int errorCount;
  private int threshold;  

  public SemanticErrorReporter(int threshold) {
    this.threshold = threshold;
    this.problems = new ArrayList();
  }

  private String format(String string) {
    return MessageFormat.format(string, new Object[0]);
  }
  
  private String format(String string, String arg){
    return MessageFormat.format(string, new Object[]{arg});
  }
  
  private String format(String string, String arg1, String arg2){
    return MessageFormat.format(string, new Object[]{arg1, arg2});
  }
  
  private String format(String string, String arg1, String arg2, String arg3){
    return MessageFormat.format(string, new Object[]{arg1, arg2, arg3});
  }
  
  private String format(
    String string, String arg1, String arg2, String arg3, String arg4){
    return MessageFormat.format(string, new Object[]{arg1, arg2, arg3, arg4});
  }
  
  private String format(String string, String[] args){
    return MessageFormat.format(string, (Object[])args);
  }
  
  private String message(String property){
    return Messages.get(property);
  }

  private void reportIncompatibleType(
    Location position, Object[] items){
    IRT.TypeRef expected = (IRT.TypeRef) items[0];
    IRT.TypeRef detected = (IRT.TypeRef) items[1];
    problem(
      position,
      format(
        message("error.semantic.incompatibleType"),
        expected.name(), detected.name()));
  }
  
  private String names(IRT.TypeRef[] types){
    StringBuffer buffer = new StringBuffer();
    if(types.length > 0){
      buffer.append(types[0].name());
      for(int i = 1; i < types.length; i++){
        buffer.append(", ");
        buffer.append(types[i].name());
      }
    }
    return new String(buffer);
  }
    
  private void reportIncompatibleOperandType(
    Location position, Object[] items){
    String operator = (String) items[0];
    IRT.TypeRef[] operands = (IRT.TypeRef[]) items[1];
    problem(
      position,
      format(message("error.semantic.incompatibleOperandType"), 
      (String)items[0], names(operands)));
  }

  private void reportLValueRequired(Location position, Object[] items){
    problem(position, format(message("error.semantic.lValueRequired")));
  }
  
  private void reportVariableNotFound(
    Location position, Object[] items){
    problem(
      position,
      format(message("error.semantic.variableNotFound"), (String)items[0]));
  }
  
  private void reportClassNotFound(
    Location position, Object[] items){
    problem(
      position,
      format(message("error.semantic.classNotFound"), (String)items[0]));
  }
  
  private void reportFieldNotFound(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.fieldNotFound"), 
        ((IRT.TypeRef)items[0]).name(), (String)items[1]));
  }
  
  private void reportMethodNotFound(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.methodNotFound"),
        ((IRT.TypeRef)items[0]).name(), (String)items[1],
        names(((IRT.TypeRef[])items[2]))));
  }
  
  private void reportAmbiguousMethod(
    Location position, Object[] items){
    Object[] item1 = (Object[])items[0];
    Object[] item2 = (Object[])items[1];
    String target1 = ((IRT.ObjectTypeRef)item1[0]).name();
    String name1 = (String)item1[1];
    String args1 = names((IRT.TypeRef[])item1[2]);
    String target2 = ((IRT.ObjectTypeRef)item2[0]).name();
    String name2 = (String)item2[1];
    String args2 = names((IRT.TypeRef[])item2[2]);
    problem(
      position,
      format(
        message("error.semantic.ambiguousMethod"),
        new String[]{target1, name1, args2, target2, name2, args2}));
  }
  
  private void reportDuplicateLocalVariable(
    Location position, Object[] items){
    problem(
      position,
      format(message("error.semantic.duplicatedVariable"), (String)items[0]));
  }
  
  private void reportDuplicateClass(
    Location position, Object[] items){
    problem(
      position, 
      format(message("error.semantic.duplicatedClass"), (String)items[0]));
  }
  
  private void reportDuplicateField(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.duplicatedField"),
        ((IRT.TypeRef)items[0]).name(), (String)items[1]));
  }
  
  private void reportDuplicateMethod(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.duplicatedMethod"),
        ((IRT.TypeRef)items[0]).name(), (String)items[1],
        names((IRT.TypeRef[])items[2])));
  }
  
  private void reportDuplicateGlobalVariable(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.duplicatedGlobalVariable"),
        (String)items[0]));
  }
  
  private void reportDuplicateFunction(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.duplicatedGlobalVariable"),
        (String)items[0], names((IRT.TypeRef[])items[1])));
  }
  
  private void reportDuplicateConstructor(
    Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.duplicatedConstructor"),
        ((IRT.TypeRef)items[0]).name(), names((IRT.TypeRef[])items[1])));
  }
  
  private void reportMethodNotAccessible(
    Location position, Object[] items){
    problem(
      position, 
      format(
        message("error.semantic.methodNotAccessible"),
        ((IRT.ObjectTypeRef)items[0]).name(),
        (String)items[1],
        names(((IRT.TypeRef[])items[2])),
        ((IRT.ClassTypeRef)items[3]).name()));
  }
  
  private void reportFieldNotAccessible(
    Location position, Object[] items){
    problem(
      position, 
      format(
        message("error.semantic.fieldNotAccessible"), 
        ((IRT.ClassTypeRef)items[0]).name(),
        (String)items[1],
        ((IRT.ClassTypeRef)items[2]).name()));
  }
  
  private void reportClassNotAccessible(
    Location position, Object[] items){
    problem(
      position, 
      format(
        message("error.semantic.classNotAccessible"), 
        ((IRT.ClassTypeRef)items[0]).name(),
        ((IRT.ClassTypeRef)items[1]).name()));
  }
  
  private void reportCyclicInheritance(
    Location position, Object[] items){
    problem(
      position, format(message("error.semantic.cyclicInheritance"),
      (String)items[0]));
  }

  private void reportCyclicDelegation(
    Location position, Object[] items){
    problem(position, message("error.semantic.cyclicDelegation"));
  }
  
  private void reportIllegalInheritance(
    Location position, Object[] items){
  }
  
  private void reportCannotReturnValue(
    Location position, Object[] items){
    problem(position, message("error.semantic.cannotReturnValue"));
  }
  
  private void reportConstructorNotFound(
    Location position, Object[] items){
    String type = ((IRT.TypeRef)items[0]).name();
    String args = names(((IRT.TypeRef[])items[1]));
    problem(
      position, 
      format(message("error.semantic.constructorNotFound"), type, args));
  }
  
  private void reportAmbiguousConstructor(Location position, Object[] items){
    Object[] item1 = (Object[])items[0];
    Object[] item2 = (Object[])items[1];
    String target1 = ((IRT.ObjectTypeRef)item1[0]).name();
    String args1 = names((IRT.TypeRef[])item1[1]);
    String target2 = ((IRT.ObjectTypeRef)item2[0]).name();
    String args2 = names((IRT.TypeRef[])item2[1]);
    problem(
      position,
      format(
        message("error.semantic.ambiguousConstructor"),
        target1, args2, target2, args2));
  }
  
  private void reportInterfaceRequied(Location position, Object[] items){
    IRT.TypeRef type = (IRT.TypeRef)items[0];
    problem(
      position, 
      format(message("error.semantic.interfaceRequired"), type.name()));
  }
  
  private void reportUnimplementedFeature(Location position, Object[] items){
    problem(
      position, message("error.semantic.unimplementedFeature"));
  }
  
  private void reportDuplicateGeneratedMethod(Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.duplicateGeneratedMethod"),
        ((IRT.TypeRef)items[0]).name(), (String)items[1],
        names((IRT.TypeRef[])items[2])));
  }
  
  private void reportIsNotBoxableType(Location position, Object[] items){
    problem(
      position,
      format(
        message("error.semantic.isNotBoxableType"),
        ((IRT.TypeRef)items[0]).name()));
  }
  
  private void problem(Location position, String message){
    problems.add(new CompileError(sourceFile, position, message));
  }
  
  public void report(int error, Location position, Object[] items){
    errorCount++;    
    switch(error){
      case Constants.INCOMPATIBLE_TYPE:
        reportIncompatibleType(position, items);
      	break;
      case Constants.INCOMPATIBLE_OPERAND_TYPE:
        reportIncompatibleOperandType(position, items);
      	break;
      case Constants.VARIABLE_NOT_FOUND:
        reportVariableNotFound(position, items);
      	break;
      case Constants.CLASS_NOT_FOUND:
        reportClassNotFound(position, items);
      	break;
      case Constants.FIELD_NOT_FOUND:
        reportFieldNotFound(position, items);
      	break;
      case Constants.METHOD_NOT_FOUND:
        reportMethodNotFound(position, items);
      	break;
      case Constants.AMBIGUOUS_METHOD:
        reportAmbiguousMethod(position, items);
      	break;
      case Constants.DUPLICATE_LOCAL_VARIABLE:
        reportDuplicateLocalVariable(position, items);
      	break;
      case Constants.DUPLICATE_CLASS:
        reportDuplicateClass(position, items);
      	break;
      case Constants.DUPLICATE_FIELD:
        reportDuplicateField(position, items);
      	break;
      case Constants.DUPLICATE_METHOD:
        reportDuplicateMethod(position, items);
      	break;
      case Constants.DUPLICATE_GLOBAL_VARIABLE:
        reportDuplicateGlobalVariable(position, items);
      	break;
      case Constants.DUPLICATE_FUNCTION:
        reportDuplicateFunction(position, items);
      	break;
      case Constants.METHOD_NOT_ACCESSIBLE:
        reportMethodNotAccessible(position, items);
      	break;
      case Constants.FIELD_NOT_ACCESSIBLE:
        reportFieldNotAccessible(position, items);
      	break;
      case Constants.CLASS_NOT_ACCESSIBLE:
        reportClassNotAccessible(position, items);
      	break;
      case Constants.CYCLIC_INHERITANCE:
        reportCyclicInheritance(position, items);
      	break;
      case Constants.CYCLIC_DELEGATION:
        reportCyclicDelegation(position, items);
      	break;
      case Constants.ILLEGAL_INHERITANCE:
        reportIllegalInheritance(position, items);
      	break;
      case Constants.CANNOT_RETURN_VALUE:
        reportCannotReturnValue(position, items);
      	break;
      case Constants.CONSTRUCTOR_NOT_FOUND:
        reportConstructorNotFound(position, items);
      	break;
      case Constants.AMBIGUOUS_CONSTRUCTOR:
        reportAmbiguousConstructor(position, items);
      	break;
      case Constants.INTERFACE_REQUIRED:
        reportInterfaceRequied(position, items);
      	break;
      case Constants.UNIMPLEMENTED_FEATURE:
        reportUnimplementedFeature(position, items);
      	break;
      case Constants.DUPLICATE_CONSTRUCTOR:
        reportDuplicateConstructor(position ,items);
      	break;
      case Constants.DUPLICATE_GENERATED_METHOD:
        reportDuplicateGeneratedMethod(position, items);
      	break;
      case Constants.IS_NOT_BOXABLE_TYPE:
        reportIsNotBoxableType(position, items);
      case Constants.LVALUE_REQUIRED:
        reportLValueRequired(position, items);
      	break;
    }
    if(errorCount >= threshold){
      throw new CompilationException(problems);
    }
  }
  
  public CompileError[] getProblems(){
    return (CompileError[]) problems.toArray(
      new CompileError[0]);
  }
  
  public void setSourceFile(String sourceFile) {
    this.sourceFile = sourceFile;
  }
}
