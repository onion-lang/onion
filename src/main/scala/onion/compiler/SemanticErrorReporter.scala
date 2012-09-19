package onion.compiler

/* ************************************************************** *
 *                                                                *
 * Copyright (c) 2005-2012, Kota Mizushima, All rights reserved.  *
 *                                                                *
 *                                                                *
 * This software is distributed under the modified BSD License.   *
 * ************************************************************** */

import java.text.MessageFormat
import java.util.ArrayList
import java.util.List
import onion.compiler.toolbox.Messages
import onion.compiler.exceptions.CompilationException

/**
 * @author Kota Mizushima
 * Date: 2005/06/23
 */
class SemanticErrorReporter(threshold: Int) {
  private val problems = new ArrayList[CompileError]
  private var sourceFile: String = null
  private var errorCount: Int = 0

  private def format(string: String): String = {
    return MessageFormat.format(string)
  }

  private def format(string: String, arg: String): String = {
    return MessageFormat.format(string, arg)
  }

  private def format(string: String, arg1: String, arg2: String): String = {
    return MessageFormat.format(string, arg1, arg2)
  }

  private def format(string: String, arg1: String, arg2: String, arg3: String): String = {
    return MessageFormat.format(string, arg1, arg2, arg3)
  }

  private def format(string: String, arg1: String, arg2: String, arg3: String, arg4: String): String = {
    return MessageFormat.format(string, arg1, arg2, arg3, arg4)
  }

  private def format(string: String, args: Array[String]): String = {
    return MessageFormat.format(string, args.asInstanceOf[Array[AnyRef]]:_*)
  }

  private def message(property: String): String = {
    return Messages.get(property)
  }

  private def reportIncompatibleType(position: Location, items: Array[AnyRef]): Unit = {
    var expected: IRT.TypeRef = items(0).asInstanceOf[IRT.TypeRef]
    var detected: IRT.TypeRef = items(1).asInstanceOf[IRT.TypeRef]
    problem(position, format(message("error.semantic.incompatibleType"), expected.name, detected.name))
  }

  private def names(types: Array[IRT.TypeRef]): String = {
    var buffer: StringBuffer = new StringBuffer

    if (types.length > 0) {
      buffer.append(types(0).name)

      {
        var i: Int = 1
        while (i < types.length) {
          {
            buffer.append(", ")
            buffer.append(types(i).name)
          }
          ({
            i += 1; i
          })
        }
      }
    }
    return new String(buffer)
  }

  private def reportIncompatibleOperandType(position: Location, items: Array[AnyRef]): Unit = {
    var operator: String = items(0).asInstanceOf[String]
    var operands: Array[IRT.TypeRef] = items(1).asInstanceOf[Array[IRT.TypeRef]]
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
    problem(position, format(message("error.semantic.fieldNotFound"), (items(0).asInstanceOf[IRT.TypeRef]).name, items(1).asInstanceOf[String]))
  }

  private def reportMethodNotFound(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.methodNotFound"), (items(0).asInstanceOf[IRT.TypeRef]).name, items(1).asInstanceOf[String], names((items(2).asInstanceOf[Array[IRT.TypeRef]]))))
  }

  private def reportAmbiguousMethod(position: Location, items: Array[AnyRef]): Unit = {
    var item1: Array[AnyRef] = items(0).asInstanceOf[Array[AnyRef]]
    var item2: Array[AnyRef] = items(1).asInstanceOf[Array[AnyRef]]
    var target1: String = (item1(0).asInstanceOf[IRT.ObjectTypeRef]).name
    var name1: String = item1(1).asInstanceOf[String]
    var args1: String = names(item1(2).asInstanceOf[Array[IRT.TypeRef]])
    var target2: String = (item2(0).asInstanceOf[IRT.ObjectTypeRef]).name
    var name2: String = item2(1).asInstanceOf[String]
    var args2: String = names(item2(2).asInstanceOf[Array[IRT.TypeRef]])
    problem(position, format(message("error.semantic.ambiguousMethod"), Array[String](target1, name1, args2, target2, name2, args2)))
  }

  private def reportDuplicateLocalVariable(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedVariable"), items(0).asInstanceOf[String]))
  }

  private def reportDuplicateClass(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedClass"), items(0).asInstanceOf[String]))
  }

  private def reportDuplicateField(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedField"), (items(0).asInstanceOf[IRT.TypeRef]).name, items(1).asInstanceOf[String]))
  }

  private def reportDuplicateMethod(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedMethod"), (items(0).asInstanceOf[IRT.TypeRef]).name, items(1).asInstanceOf[String], names(items(2).asInstanceOf[Array[IRT.TypeRef]])))
  }

  private def reportDuplicateGlobalVariable(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedGlobalVariable"), items(0).asInstanceOf[String]))
  }

  private def reportDuplicateFunction(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedGlobalVariable"), items(0).asInstanceOf[String], names(items(1).asInstanceOf[Array[IRT.TypeRef]])))
  }

  private def reportDuplicateConstructor(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicatedConstructor"), (items(0).asInstanceOf[IRT.TypeRef]).name, names(items(1).asInstanceOf[Array[IRT.TypeRef]])))
  }

  private def reportMethodNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.methodNotAccessible"), (items(0).asInstanceOf[IRT.ObjectTypeRef]).name, items(1).asInstanceOf[String], names((items(2).asInstanceOf[Array[IRT.TypeRef]])), (items(3).asInstanceOf[IRT.ClassTypeRef]).name))
  }

  private def reportFieldNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.fieldNotAccessible"), (items(0).asInstanceOf[IRT.ClassTypeRef]).name, items(1).asInstanceOf[String], (items(2).asInstanceOf[IRT.ClassTypeRef]).name))
  }

  private def reportClassNotAccessible(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.classNotAccessible"), (items(0).asInstanceOf[IRT.ClassTypeRef]).name, (items(1).asInstanceOf[IRT.ClassTypeRef]).name))
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
    var `type` : String = (items(0).asInstanceOf[IRT.TypeRef]).name
    var args: String = names((items(1).asInstanceOf[Array[IRT.TypeRef]]))
    problem(position, format(message("error.semantic.constructorNotFound"), `type`, args))
  }

  private def reportAmbiguousConstructor(position: Location, items: Array[AnyRef]): Unit = {
    var item1: Array[AnyRef] = items(0).asInstanceOf[Array[AnyRef]]
    var item2: Array[AnyRef] = items(1).asInstanceOf[Array[AnyRef]]
    var target1: String = (item1(0).asInstanceOf[IRT.ObjectTypeRef]).name
    var args1: String = names(item1(1).asInstanceOf[Array[IRT.TypeRef]])
    var target2: String = (item2(0).asInstanceOf[IRT.ObjectTypeRef]).name
    var args2: String = names(item2(1).asInstanceOf[Array[IRT.TypeRef]])
    problem(position, format(message("error.semantic.ambiguousConstructor"), target1, args2, target2, args2))
  }

  private def reportInterfaceRequied(position: Location, items: Array[AnyRef]): Unit = {
    var `type` : IRT.TypeRef = items(0).asInstanceOf[IRT.TypeRef]
    problem(position, format(message("error.semantic.interfaceRequired"), `type`.name))
  }

  private def reportUnimplementedFeature(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, message("error.semantic.unimplementedFeature"))
  }

  private def reportDuplicateGeneratedMethod(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.duplicateGeneratedMethod"), (items(0).asInstanceOf[IRT.TypeRef]).name, items(1).asInstanceOf[String], names(items(2).asInstanceOf[Array[IRT.TypeRef]])))
  }

  private def reportIsNotBoxableType(position: Location, items: Array[AnyRef]): Unit = {
    problem(position, format(message("error.semantic.isNotBoxableType"), (items(0).asInstanceOf[IRT.TypeRef]).name))
  }

  private def problem(position: Location, message: String): Unit = {
    problems.add(new CompileError(sourceFile, position, message))
  }

  def report(error: Int, position: Location, items: Array[AnyRef]): Unit = {
    ({
      errorCount += 1; errorCount
    })
    error match {
      case SemanticErrorConstants.INCOMPATIBLE_TYPE =>
        reportIncompatibleType(position, items)
      case SemanticErrorConstants.INCOMPATIBLE_OPERAND_TYPE =>
        reportIncompatibleOperandType(position, items)
      case SemanticErrorConstants.VARIABLE_NOT_FOUND =>
        reportVariableNotFound(position, items)
      case SemanticErrorConstants.CLASS_NOT_FOUND =>
        reportClassNotFound(position, items)
      case SemanticErrorConstants.FIELD_NOT_FOUND =>
        reportFieldNotFound(position, items)
      case SemanticErrorConstants.METHOD_NOT_FOUND =>
        reportMethodNotFound(position, items)
      case SemanticErrorConstants.AMBIGUOUS_METHOD =>
        reportAmbiguousMethod(position, items)
      case SemanticErrorConstants.DUPLICATE_LOCAL_VARIABLE =>
        reportDuplicateLocalVariable(position, items)
      case SemanticErrorConstants.DUPLICATE_CLASS =>
        reportDuplicateClass(position, items)
      case SemanticErrorConstants.DUPLICATE_FIELD =>
        reportDuplicateField(position, items)
      case SemanticErrorConstants.DUPLICATE_METHOD =>
        reportDuplicateMethod(position, items)
      case SemanticErrorConstants.DUPLICATE_GLOBAL_VARIABLE =>
        reportDuplicateGlobalVariable(position, items)
      case SemanticErrorConstants.DUPLICATE_FUNCTION =>
        reportDuplicateFunction(position, items)
      case SemanticErrorConstants.METHOD_NOT_ACCESSIBLE =>
        reportMethodNotAccessible(position, items)
      case SemanticErrorConstants.FIELD_NOT_ACCESSIBLE =>
        reportFieldNotAccessible(position, items)
      case SemanticErrorConstants.CLASS_NOT_ACCESSIBLE =>
        reportClassNotAccessible(position, items)
      case SemanticErrorConstants.CYCLIC_INHERITANCE =>
        reportCyclicInheritance(position, items)
      case SemanticErrorConstants.CYCLIC_DELEGATION =>
        reportCyclicDelegation(position, items)
      case SemanticErrorConstants.ILLEGAL_INHERITANCE =>
        reportIllegalInheritance(position, items)
      case SemanticErrorConstants.CANNOT_RETURN_VALUE =>
        reportCannotReturnValue(position, items)
      case SemanticErrorConstants.CONSTRUCTOR_NOT_FOUND =>
        reportConstructorNotFound(position, items)
      case SemanticErrorConstants.AMBIGUOUS_CONSTRUCTOR =>
        reportAmbiguousConstructor(position, items)
      case SemanticErrorConstants.INTERFACE_REQUIRED =>
        reportInterfaceRequied(position, items)
      case SemanticErrorConstants.UNIMPLEMENTED_FEATURE =>
        reportUnimplementedFeature(position, items)
      case SemanticErrorConstants.DUPLICATE_CONSTRUCTOR =>
        reportDuplicateConstructor(position, items)
      case SemanticErrorConstants.DUPLICATE_GENERATED_METHOD =>
        reportDuplicateGeneratedMethod(position, items)
      case SemanticErrorConstants.IS_NOT_BOXABLE_TYPE =>
        reportIsNotBoxableType(position, items)
      case SemanticErrorConstants.LVALUE_REQUIRED =>
        reportLValueRequired(position, items)
    }
    if (errorCount >= threshold) {
      throw new CompilationException(problems)
    }
  }

  def getProblems: Array[CompileError] = {
    return problems.toArray(new Array[CompileError](0)).asInstanceOf[Array[CompileError]]
  }

  def setSourceFile(sourceFile: String): Unit = {
    this.sourceFile = sourceFile
  }


}