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
import onion.compiler.toolbox.Message
import onion.compiler.exceptions.CompilationException

/**
 * @author Kota Mizushima
 *
 */
class SemanticErrorReporter(threshold: Int) {
  private val problems = Buffer[CompileError]()
  private var sourceFile: String = null
  private var errorCount: Int = 0

  private def format(string: String): String = {
    MessageFormat.format(string)
  }

  private def format(string: String, arg: String): String = {
    MessageFormat.format(string, arg)
  }

  private def format(string: String, arg1: String, arg2: String): String = {
    MessageFormat.format(string, arg1, arg2)
  }

  private def format(string: String, arg1: String, arg2: String, arg3: String): String = {
    MessageFormat.format(string, arg1, arg2, arg3)
  }

  private def format(string: String, arg1: String, arg2: String, arg3: String, arg4: String): String = {
    MessageFormat.format(string, arg1, arg2, arg3, arg4)
  }

  private def format(string: String, args: Array[String]): String = {
    MessageFormat.format(string, args.asInstanceOf[Array[AnyRef]]:_*)
  }

  private[this] def message(property: String): String = Message(property)

  private def reportIllegalMethodCall(position: Location, items: Array[AnyRef]): Unit = {
    val receiver = items(0).asInstanceOf[IRT.ClassType].name
    val methodName= items(1).asInstanceOf[String]
    problem(position, format(message("error.semantic.illegalMethodCall"), receiver, methodName))
  }

  private def reportIncompatibleType(position: Location, items: Array[AnyRef]): Unit = {
    val expected: IRT.Type = items(0).asInstanceOf[IRT.Type]
    val detected: IRT.Type = items(1).asInstanceOf[IRT.Type]
    problem(position, format(message("error.semantic.incompatibleType"), expected.name, detected.name))
  }

  private def names(types: Array[IRT.Type]): String = {
    val buffer = new StringBuffer
    if (types.length > 0) {
      buffer.append(types(0).name)
      var i: Int = 1
      while (i < types.length) {
        buffer.append(", ")
        buffer.append(types(i).name)
        i += 1
      }
    }
    new String(buffer)
  }

  private def reportIncompatibleOperandType(position: Location, items: Array[AnyRef]): Unit = {
    val operator: String = items(0).asInstanceOf[String]
    val operands: Array[IRT.Type] = items(1).asInstanceOf[Array[IRT.Type]]
    problem(position, format(message("error.semantic.incompatibleOperandType"), items(0).asInstanceOf[String], names(operands)))
  }

  private def reportLValueRequired(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.lValueRequired")))
  }

  private def reportVariableNotFound(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.variableNotFound"), items(0).asInstanceOf[String]))
  }

  private def reportClassNotFound(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.classNotFound"), items(0).asInstanceOf[String]))
  }

  private def reportFieldNotFound(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.fieldNotFound"), (items(0).asInstanceOf[IRT.Type]).name, items(1).asInstanceOf[String]))
  }

  private def reportMethodNotFound(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.methodNotFound"), (items(0).asInstanceOf[IRT.Type]).name, items(1).asInstanceOf[String], names((items(2).asInstanceOf[Array[IRT.Type]]))))
  }

  private def reportAmbiguousMethod(position: Location, items: Array[AnyRef]): Unit = {
    val item1: Array[AnyRef] = items(0).asInstanceOf[Array[AnyRef]]
    val item2: Array[AnyRef] = items(1).asInstanceOf[Array[AnyRef]]
    val target1: String = (item1(0).asInstanceOf[IRT.ObjectType]).name
    val name1: String = item1(1).asInstanceOf[String]
    val args1: String = names(item1(2).asInstanceOf[Array[IRT.Type]])
    val target2: String = (item2(0).asInstanceOf[IRT.ObjectType]).name
    val name2: String = item2(1).asInstanceOf[String]
    val args2: String = names(item2(2).asInstanceOf[Array[IRT.Type]])
    problem(position, format(message("error.semantic.ambiguousMethod"), Array[String](target1, name1, args2, target2, name2, args2)))
  }

  private def reportDuplicateLocalVariable(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedVariable"), items(0).asInstanceOf[String]))
  }

  private def reportDuplicateClass(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedClass"), items(0).asInstanceOf[String]))
  }

  private def reportDuplicateField(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedField"), (items(0).asInstanceOf[IRT.Type]).name, items(1).asInstanceOf[String]))
  }

  private def reportDuplicateMethod(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedMethod"), (items(0).asInstanceOf[IRT.Type]).name, items(1).asInstanceOf[String], names(items(2).asInstanceOf[Array[IRT.Type]])))
  }

  private def reportDuplicateGlobalVariable(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedGlobalVariable"), items(0).asInstanceOf[String]))
  }

  private def reportDuplicateFunction(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedGlobalVariable"), items(0).asInstanceOf[String], names(items(1).asInstanceOf[Array[IRT.Type]])))
  }

  private def reportDuplicateConstructor(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedConstructor"), (items(0).asInstanceOf[IRT.Type]).name, names(items(1).asInstanceOf[Array[IRT.Type]])))
  }

  private def reportMethodNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.methodNotAccessible"), (items(0).asInstanceOf[IRT.ObjectType]).name, items(1).asInstanceOf[String], names((items(2).asInstanceOf[Array[IRT.Type]])), (items(3).asInstanceOf[IRT.ClassType]).name))
  }

  private def reportFieldNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.fieldNotAccessible"), (items(0).asInstanceOf[IRT.ClassType]).name, items(1).asInstanceOf[String], (items(2).asInstanceOf[IRT.ClassType]).name))
  }

  private def reportClassNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.classNotAccessible"), (items(0).asInstanceOf[IRT.ClassType]).name, (items(1).asInstanceOf[IRT.ClassType]).name))
  }

  private def reportCyclicInheritance(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.cyclicInheritance"), items(0).asInstanceOf[String]))
  }

  private def reportCyclicDelegation(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, message("error.semantic.cyclicDelegation"))
  }

  private def reportIllegalInheritance(position: Location, items: Array[AnyRef]): Unit = {
  }

  private def reportCannotReturnValue(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, message("error.semantic.cannotReturnValue"))
  }

  private def reportConstructorNotFound(position: Location, items: Array[AnyRef]): Unit = {
    val `type` : String = (items(0).asInstanceOf[IRT.Type]).name
    val args: String = names((items(1).asInstanceOf[Array[IRT.Type]]))
    problem(position, format(message("error.semantic.constructorNotFound"), `type`, args))
  }

  private def reportAmbiguousConstructor(position: Location, items: Array[AnyRef]): Unit = {
    val item1: Array[AnyRef] = items(0).asInstanceOf[Array[AnyRef]]
    val item2: Array[AnyRef] = items(1).asInstanceOf[Array[AnyRef]]
    val target1: String = (item1(0).asInstanceOf[IRT.ObjectType]).name
    val args1: String = names(item1(1).asInstanceOf[Array[IRT.Type]])
    val target2: String = (item2(0).asInstanceOf[IRT.ObjectType]).name
    val args2: String = names(item2(1).asInstanceOf[Array[IRT.Type]])
    problem(position, format(message("error.semantic.ambiguousConstructor"), target1, args2, target2, args2))
  }

  private def reportInterfaceRequied(position: Location, items: Array[AnyRef]): Unit = {
    val `type` : IRT.Type = items(0).asInstanceOf[IRT.Type]
    problem(position, format(message("error.semantic.interfaceRequired"), `type`.name))
  }

  private def reportUnimplementedFeature(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, message("error.semantic.unimplementedFeature"))
  }

  private def reportDuplicateGeneratedMethod(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicateGeneratedMethod"), (items(0).asInstanceOf[IRT.Type]).name, items(1).asInstanceOf[String], names(items(2).asInstanceOf[Array[IRT.Type]])))
  }

  private def reportIsNotBoxableType(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.isNotBoxableType"), (items(0).asInstanceOf[IRT.Type]).name))
  }

  private def problem(position: Location, message: String): Unit = {
    problems.append(new CompileError(sourceFile, position, message))
  }

  def report(error: SemanticError, position: Location, items: Array[AnyRef]): Unit = {
    errorCount += 1
    error match {
      case SemanticError.ILLEGAL_METHOD_CALL =>
        reportIllegalMethodCall(position, items)
      case SemanticError.INCOMPATIBLE_TYPE =>
        reportIncompatibleType(position, items)
      case SemanticError.INCOMPATIBLE_OPERAND_TYPE =>
        reportIncompatibleOperandType(position, items)
      case SemanticError.VARIABLE_NOT_FOUND =>
        reportVariableNotFound(position, items)
      case SemanticError.CLASS_NOT_FOUND =>
        reportClassNotFound(position, items)
      case SemanticError.FIELD_NOT_FOUND =>
        reportFieldNotFound(position, items)
      case SemanticError.METHOD_NOT_FOUND =>
        reportMethodNotFound(position, items)
      case SemanticError.AMBIGUOUS_METHOD =>
        reportAmbiguousMethod(position, items)
      case SemanticError.DUPLICATE_LOCAL_VARIABLE =>
        reportDuplicateLocalVariable(position, items)
      case SemanticError.DUPLICATE_CLASS =>
        reportDuplicateClass(position, items)
      case SemanticError.DUPLICATE_FIELD =>
        reportDuplicateField(position, items)
      case SemanticError.DUPLICATE_METHOD =>
        reportDuplicateMethod(position, items)
      case SemanticError.DUPLICATE_GLOBAL_VARIABLE =>
        reportDuplicateGlobalVariable(position, items)
      case SemanticError.DUPLICATE_FUNCTION =>
        reportDuplicateFunction(position, items)
      case SemanticError.METHOD_NOT_ACCESSIBLE =>
        reportMethodNotAccessible(position, items)
      case SemanticError.FIELD_NOT_ACCESSIBLE =>
        reportFieldNotAccessible(position, items)
      case SemanticError.CLASS_NOT_ACCESSIBLE =>
        reportClassNotAccessible(position, items)
      case SemanticError.CYCLIC_INHERITANCE =>
        reportCyclicInheritance(position, items)
      case SemanticError.CYCLIC_DELEGATION =>
        reportCyclicDelegation(position, items)
      case SemanticError.ILLEGAL_INHERITANCE =>
        reportIllegalInheritance(position, items)
      case SemanticError.CANNOT_RETURN_VALUE =>
        reportCannotReturnValue(position, items)
      case SemanticError.CONSTRUCTOR_NOT_FOUND =>
        reportConstructorNotFound(position, items)
      case SemanticError.AMBIGUOUS_CONSTRUCTOR =>
        reportAmbiguousConstructor(position, items)
      case SemanticError.INTERFACE_REQUIRED =>
        reportInterfaceRequied(position, items)
      case SemanticError.UNIMPLEMENTED_FEATURE =>
        reportUnimplementedFeature(position, items)
      case SemanticError.DUPLICATE_CONSTRUCTOR =>
        reportDuplicateConstructor(position, items)
      case SemanticError.DUPLICATE_GENERATED_METHOD =>
        reportDuplicateGeneratedMethod(position, items)
      case SemanticError.IS_NOT_BOXABLE_TYPE =>
        reportIsNotBoxableType(position, items)
      case SemanticError.LVALUE_REQUIRED =>
        reportLValueRequired(position, items)
    }
    if (errorCount >= threshold) {
      throw new CompilationException(problems)
    }
  }

  def getProblems: Array[CompileError] = problems.toArray

  def setSourceFile(sourceFile: String): Unit = {
    this.sourceFile = sourceFile
  }


}