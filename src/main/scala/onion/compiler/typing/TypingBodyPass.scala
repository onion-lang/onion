package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Constants.*
import onion.compiler.TypedAST.UnaryTerm.Constants.*
import onion.compiler.toolbox.{Boxing, Classes}

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*
import scala.collection.mutable.{Buffer, HashMap, Map, Set => MutableSet, Stack}

final class TypingBodyPass(private val typing: Typing, private val unit: AST.CompilationUnit) {
  import typing.*
  def run(): Unit = runUnit()

  def processNodes(nodes: Array[AST.Expression], typeRef: Type, bind: ClosureLocalBinding, context: LocalContext): Term = {
    val expressions = new Array[Term](nodes.length)
    var error: Boolean = false
    for(i <- 0 until nodes.length){
      val expressionOpt = typed(nodes(i), context)
      expressions(i) = expressionOpt.getOrElse(null)
      if(expressions(i) == null) {
        error = true
      } else if (!TypeRules.isAssignable(typeRef, expressions(i).`type`)) {
        report(INCOMPATIBLE_TYPE, nodes(i), typeRef, expressions(i).`type`)
        error = true
      } else {
        if (expressions(i).isBasicType && expressions(i).`type` != typeRef) expressions(i) = new AsInstanceOf(expressions(i), typeRef)
        if (expressions(i).isReferenceType && expressions(i).`type` != rootClass) expressions(i) = new AsInstanceOf(expressions(i), rootClass)
      }
    }
    if (!error) {
      var node: Term = if(expressions(0).isReferenceType) {
        createEquals(BinaryTerm.Constants.EQUAL, new RefLocal(bind), expressions(0))
      } else {
        new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), expressions(0))
      }
      for(i <- 1 until expressions.length) {
        node = new BinaryTerm(LOGICAL_OR, BasicType.BOOLEAN, node, new BinaryTerm(EQUAL, BasicType.BOOLEAN, new RefLocal(bind), expressions(i)))
      }
      node
    } else {
      null
    }
  }
  def processAssignable(node: AST.Node, a: Type, b: Term): Term = {
    if (b == null) return null
    if (a == b.`type`) return b
    if (!TypeRules.isAssignable(a, b.`type`)) {
      report(INCOMPATIBLE_TYPE, node, a, b.`type`)
      return null
    }
    new AsInstanceOf(node.location, b, a)
  }
  def openClosure[A](context: LocalContext)(block: => A): A = {
    val tmp = context.isClosure
    try {
      context.setClosure(true)
      block
    }finally{
      context.setClosure(tmp)
    }
  }
  def openFrame[A](context: LocalContext)(block: => A): A = context.openFrame(block)
  def processMethodDeclaration(node: AST.MethodDeclaration): Unit = {
    val method = lookupKernelNode(node).asInstanceOf[MethodDefinition]
    if (method == null) return
    if (node.block == null) return
    val methodTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(typeParams_ ++ methodTypeParams) {
      val context = new LocalContext
      if((method.modifier & AST.M_STATIC) != 0) {
        context.setStatic(true)
      }
      context.setMethod(method)
      val arguments = method.arguments
      for(i <- 0 until arguments.length) {
        context.add(node.args(i).name, arguments(i))
      }
      val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], method.returnType)
      method.setBlock(block)
      method.setFrame(context.getContextFrame)
    }
  }
  def processConstructorDeclaration(node: AST.ConstructorDeclaration): Unit = {
    val constructor = lookupKernelNode(node).asInstanceOf[ConstructorDefinition]
    if (constructor == null) return
    val context = new LocalContext
    context.setConstructor(constructor)
    val args = constructor.getArgs
    for(i <- 0 until args.length) {
      context.add(node.args(i).name, args(i))
    }
    val params = typedTerms(node.superInits.toArray, context)
    val currentClass = definition_
    val superClass = currentClass.superClass
    val matched = superClass.findConstructor(params)
    if (matched.length == 0) {
      report(CONSTRUCTOR_NOT_FOUND, node, superClass, types(params))
    }else if (matched.length > 1) {
      report(AMBIGUOUS_CONSTRUCTOR, node, Array[AnyRef](superClass, types(params)), Array[AnyRef](superClass, types(params)))
    }else {
      val init = new Super(superClass, matched(0).getArgs, params)
      val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], BasicType.VOID)
      constructor.superInitializer = init
      constructor.block = block
      constructor.frame = context.getContextFrame
    }
  }
  def processClassDeclaration(node: AST.ClassDeclaration, context: LocalContext): Unit = {
    definition_ = lookupKernelNode(node).asInstanceOf[ClassDefinition]
    mapper_ = find(definition_.name)
    val classTypeParams = declaredTypeParams_.getOrElse(node, Seq())
    openTypeParams(emptyTypeParams ++ classTypeParams) {
      for(section <- node.defaultSection; member <- section.members) {
        member match {
          case member: AST.FieldDeclaration =>
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case member: AST.DelegatedFieldDeclaration =>
        }
      }
      for(section <- node.sections; member <- section.members) {
        member match {
          case member: AST.FieldDeclaration =>
          case member: AST.MethodDeclaration =>
            processMethodDeclaration(member)
          case member: AST.ConstructorDeclaration =>
            processConstructorDeclaration(member)
          case member: AST.DelegatedFieldDeclaration =>
        }
      }
    }
  }
  def processInterfaceDeclaration(node: AST.InterfaceDeclaration, context: LocalContext): Unit = { () }
  def processFunctionDeclaration(node: AST.FunctionDeclaration, context: LocalContext): Unit = {
    val function = lookupKernelNode(node).asInstanceOf[MethodDefinition]
    if (function == null) return
    val context = new LocalContext
    if ((function.modifier & AST.M_STATIC) != 0) {
      context.setStatic(true)
    }
    context.setMethod(function)
    val arguments = function.arguments
    for(i <- 0 until arguments.length) {
      context.add(node.args(i).name, arguments(i))
    }
    val block = addReturnNode(translate(node.block, context).asInstanceOf[StatementBlock], function.returnType)
    function.setBlock(block)
    function.setFrame(context.getContextFrame)
  }
  def processGlobalVariableDeclaration(node: AST.GlobalVariableDeclaration, context: LocalContext): Unit = {()}
  def processLocalAssign(node: AST.Assignment, context: LocalContext): Term = {
    var value: Term = typed(node.rhs, context).getOrElse(null)
    if (value == null) return null
    val id = node.lhs.asInstanceOf[AST.Id]
    val bind = context.lookup(id.name)
    if (bind == null) {
      report(VARIABLE_NOT_FOUND, id, id.name)
      return null
    }
    if (!bind.isMutable) {
      report(CANNOT_ASSIGN_TO_VAL, id, id.name)
      return null
    }

    val frame = bind.frameIndex
    val index = bind.index
    val leftType = bind.tp
    value = processAssignable(node.rhs, leftType, value)
    if (value != null) new SetLocal(frame, index, leftType, value) else null
  }
  // Removed: processThisFieldAssign - use this.field or self.field instead
  def processArrayAssign(node: AST.Assignment, context: LocalContext): Term = {
    var value = typed(node.rhs, context).getOrElse(null)
    val indexing = node.lhs.asInstanceOf[AST.Indexing]
    val target = typed(indexing.lhs, context).getOrElse(null)
    val index = typed(indexing.rhs, context).getOrElse(null)
    if (value == null || target == null || index == null) return null
    if (target.isBasicType) {
      report(INCOMPATIBLE_TYPE, indexing.lhs, rootClass, target.`type`)
      return null
    }
    if (target.isArrayType) {
      val targetType = target.`type`.asInstanceOf[ArrayType]
      if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
        report(INCOMPATIBLE_TYPE, indexing.rhs, BasicType.INT, index.`type`)
        return null
      }
      value = processAssignable(node.rhs, targetType.base, value)
      if (value == null) return null
      new SetArray(target, index, value)
    }else {
      val params = Array[Term](index, value)
      tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "set", Array[Term](index, value)) match {
        case Left(_) =>
          report(METHOD_NOT_FOUND, node, target.`type`, "set", types(params))
          null
        case Right(method) =>
          new Call(target, method, params)
      }
    }
  }
  def processMemberAssign(node: AST.Assignment, context: LocalContext): Term = {
    node match {
      case target@AST.Assignment(loc, node@AST.MemberSelection(_, _, _), expression) =>
        val contextClass = definition_
        val target = typed(node.target, context).getOrElse(null)
        if (target == null) return null
        if (target.`type`.isBasicType || target.`type`.isNullType) {
          report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
          return null
        }
        val targetType = target.`type`.asInstanceOf[ObjectType]
        if (!MemberAccess.ensureTypeAccessible(typing, node, targetType, contextClass)) return null
        val name = node.name
        val field: FieldRef = MemberAccess.findField(targetType, name)
        val value: Term = typed(expression, context).getOrElse(null)
        if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
          if (Modifier.isFinal(field.modifier) && (context.constructor == null || !target.isInstanceOf[This])) {
            report(CANNOT_ASSIGN_TO_VAL, node, field.name)
            return null
          }
          val classSubst = TypeSubstitution.classSubstitution(target.`type`)
          val expected = TypeSubstitution.substituteType(field.`type`, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
          val term = processAssignable(expression, expected, value)
          if (term == null) return null
          return new SetField(target, field, term)
        }
        tryFindMethod(node, targetType, setter(name), Array[Term](value)) match {
          case Right(method) =>
            new Call(target, method, Array[Term](value))
          case Left(_) =>
            null
        }
      case _ =>
        report(UNIMPLEMENTED_FEATURE, node)
        null
    }
  }
  def processEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left: Term = typed(node.lhs, context).getOrElse(null)
    var right: Term = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType: Type = left.`type`
    val rightType: Type = right.`type`
    if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    if (left.isBasicType && right.isBasicType) {
      if (hasNumericType(left) && hasNumericType(right)) {
        val resultType = promote(leftType, rightType)
        if (resultType != left.`type`) left = new AsInstanceOf(left, resultType)
        if (resultType != right.`type`) right = new AsInstanceOf(right, resultType)
      }
      else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
    }
    else if (left.isReferenceType && right.isReferenceType) {
      return createEquals(kind, left, right)
    }
    new BinaryTerm(kind, BasicType.BOOLEAN, left, right)
  }
  def processShiftExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left: Term = typed(node.lhs, context).getOrElse(null)
    var right: Term = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    if (!left.`type`.isBasicType) {
      val params = Array[Term](right)
      tryFindMethod(node, left.`type`.asInstanceOf[ObjectType], "add", params) match {
        case Left(_) =>
          report(METHOD_NOT_FOUND, node, left.`type`, "add", types(params))
          return null
        case Right(method) =>
          return new Call(left, method, params)
      }
    }
    if (!right.`type`.isBasicType) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftType: BasicType = left.`type`.asInstanceOf[BasicType]
    val rightType: BasicType = right.`type`.asInstanceOf[BasicType]
    if ((!leftType.isInteger) || (!rightType.isInteger)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftResultType = promoteInteger(leftType)
    if (leftResultType != leftType) {
      left = new AsInstanceOf(left, leftResultType)
    }
    if (rightType != BasicType.INT) {
      right = new AsInstanceOf(right, BasicType.INT)
    }
    new BinaryTerm(kind, BasicType.BOOLEAN, left, right)
  }
  def processComparableExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType = left.`type`
    val rightType = right.`type`
    if ((!numeric(left.`type`)) || (!numeric(right.`type`))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      null
    } else {
      val resultType = promote(leftType, rightType)
      val newLeft = if (leftType != resultType)  new AsInstanceOf(left, resultType) else left
      val newRight = if(rightType != resultType) new AsInstanceOf(right, resultType) else right
      Array[Term](newLeft, newRight)
    }
  }
  def processBitExpression(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    if ((!left.isBasicType) || (!right.isBasicType)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val leftType = left.`type`.asInstanceOf[BasicType]
    val rightType = right.`type`.asInstanceOf[BasicType]
    var resultType: Type = null
    if (leftType.isInteger && rightType.isInteger) {
      resultType = promote(leftType, rightType)
    } else if (leftType.isBoolean && rightType.isBoolean) {
      resultType = BasicType.BOOLEAN
    } else {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    val newLeft = if (left.`type` != resultType) new AsInstanceOf(left, resultType) else left
    val newRight = if (right.`type` != resultType) new AsInstanceOf(right, resultType) else right
    new BinaryTerm(kind, resultType, newLeft, newRight)
  }
  def processLogicalExpression(node: AST.BinaryExpression, context: LocalContext): Array[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType: Type = left.`type`
    val rightType: Type = right.`type`
    if ((leftType != BasicType.BOOLEAN) || (rightType != BasicType.BOOLEAN)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      null
    } else {
      Array[Term](left, right)
    }
  }
  def processRefEquals(kind: Int, node: AST.BinaryExpression, context: LocalContext): Term = {
    var left = typed(node.lhs, context).getOrElse(null)
    var right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return null
    val leftType = left.`type`
    val rightType = right.`type`
    if ((left.isBasicType && (!right.isBasicType)) || ((!left.isBasicType) && (right.isBasicType))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
      return null
    }
    if (left.isBasicType && right.isBasicType) {
      if (hasNumericType(left) && hasNumericType(right)) {
        val resultType: Type = promote(leftType, rightType)
        if (resultType != left.`type`) {
          left = new AsInstanceOf(left, resultType)
        }
        if (resultType != right.`type`) {
          right = new AsInstanceOf(right, resultType)
        }
      } else if (leftType != BasicType.BOOLEAN || rightType != BasicType.BOOLEAN) {
        report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](leftType, rightType))
        return null
      }
    }
    new BinaryTerm(kind, BasicType.BOOLEAN, left, right)
  }
  def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] = {
    var failed = false
    val result = nodes.map{node => typed(node, context).getOrElse{failed = true; null}}
    if(failed) null else result
  }

  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val contextClass = definition_
    val target = typed(node.target, context).getOrElse(null)
    if (target == null) return None
    if (target.`type`.isBasicType || target.`type`.isNullType) {
      report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
      return None
    }
    val targetType = target.`type`.asInstanceOf[ObjectType]
    if (!MemberAccess.ensureTypeAccessible(typing, node, targetType, contextClass)) return None
    val name = node.name
    if (target.`type`.isArrayType) {
      if (name.equals("length") || name.equals("size")) {
        return Some(new ArrayLength(target))
      } else {
        return None
      }
    }
    val field = MemberAccess.findField(targetType, name)
    if (field != null && MemberAccess.isMemberAccessible(field, definition_)) {
      val ref = new RefField(target, field)
      val castType =
        TypeSubstitution.substituteType(ref.`type`, TypeSubstitution.classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
      return Some(if (castType eq ref.`type`) ref else new AsInstanceOf(ref, castType))
    }

    tryFindMethod(node, targetType, name, new Array[Term](0)) match {
      case Right(method) =>
        val call = new Call(target, method, new Array[Term](0))
        val castType =
          TypeSubstitution.substituteType(method.returnType, TypeSubstitution.classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
        return Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      case Left(continuable) =>
        if (!continuable) return None
    }
    tryFindMethod(node, targetType, getter(name), new Array[Term](0)) match {
      case Right(method) =>
        val call = new Call(target, method, new Array[Term](0))
        val castType =
          TypeSubstitution.substituteType(method.returnType, TypeSubstitution.classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
        return Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      case Left(continuable) =>
        if (!continuable) return None
    }
    tryFindMethod(node, targetType, getterBoolean(name), new Array[Term](0)) match {
      case Right(method) =>
        val call = new Call(target, method, new Array[Term](0))
        val castType =
          TypeSubstitution.substituteType(method.returnType, TypeSubstitution.classSubstitution(target.`type`), scala.collection.immutable.Map.empty, defaultToBound = true)
        Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      case Left(_) =>
        if (field == null) {
          report(FIELD_NOT_FOUND, node, targetType, node.name)
        } else {
          report(FIELD_NOT_ACCESSIBLE, node, targetType, node.name, definition_)
        }
        None
    }
  }

  def typeMethodCall(node: AST.MethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val target = typed(node.target, context).getOrElse(null)
    if (target == null) return None
    val params = typedTerms(node.args.toArray, context)
    if (params == null) return None
    val targetType = target.`type`.asInstanceOf[ObjectType]
    val name = node.name
    val methods = MethodResolution.findMethods(targetType, name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, targetType, name, types(params))
      None
    } else if (methods.length > 1) {
      report(
        AMBIGUOUS_METHOD,
        node,
        Array[AnyRef](methods(0).affiliation, name, methods(0).arguments),
        Array[AnyRef](methods(1).affiliation, name, methods(1).arguments)
      )
      None
    } else if ((methods(0).modifier & AST.M_STATIC) != 0) {
      report(ILLEGAL_METHOD_CALL, node, methods(0).affiliation, name, methods(0).arguments)
      None
    } else {
      val method = methods(0)
      val classSubst = TypeSubstitution.classSubstitution(target.`type`)
      val methodSubst =
        if (node.typeArgs.nonEmpty) {
          GenericMethodTypeArguments.explicit(typing, node, method, node.typeArgs, classSubst) match {
            case Some(subst) => subst
            case None => return None
          }
        } else {
          GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected)
        }

      val expectedArgs = method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, methodSubst, defaultToBound = true))
      var i = 0
      while (i < params.length) {
        params(i) = processAssignable(node.args(i), expectedArgs(i), params(i))
        if (params(i) == null) return None
        i += 1
      }

      val call = new Call(target, method, params)
      val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
      Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
    }
  }

  def typeUnqualifiedMethodCall(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    var params = typedTerms(node.args.toArray, context)
    if (params == null) return None
    val targetType = definition_
    val methods = targetType.findMethod(node.name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, targetType, node.name, types(params))
      None
    } else if (methods.length > 1) {
      report(
        AMBIGUOUS_METHOD,
        node,
        Array[AnyRef](methods(0).affiliation, node.name, methods(0).arguments),
        Array[AnyRef](methods(1).affiliation, node.name, methods(1).arguments)
      )
      None
    } else {
      val method = methods(0)
      val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty
      val methodSubst =
        if (node.typeArgs.nonEmpty) {
          GenericMethodTypeArguments.explicit(typing, node, method, node.typeArgs, classSubst) match {
            case Some(subst) => subst
            case None => return None
          }
        } else {
          GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected)
        }

      val expectedArgs = method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, methodSubst, defaultToBound = true))
      var i = 0
      while (i < params.length) {
        params(i) = processAssignable(node.args(i), expectedArgs(i), params(i))
        if (params(i) == null) return None
        i += 1
      }

      if ((methods(0).modifier & AST.M_STATIC) != 0) {
        val call = new CallStatic(targetType, method, params)
        val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
        Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      } else {
        if (context.isClosure) {
          val call = new Call(new OuterThis(targetType), method, params)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        } else {
          val call = new Call(new This(targetType), method, params)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        }
      }
    }
  }

  def typeStaticMemberSelection(node: AST.StaticMemberSelection): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    if (typeRef == null) return None
    val field = MemberAccess.findField(typeRef, node.name)
    if (field == null) {
      report(FIELD_NOT_FOUND, node, typeRef, node.name)
      None
    } else {
      Some(new RefStaticField(typeRef, field))
    }
  }

  def typeStaticMethodCall(node: AST.StaticMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    val parameters = typedTerms(node.args.toArray, context)
    if (typeRef == null || parameters == null) {
      None
    } else {
      if (node.typeArgs.nonEmpty) {
        val methods = typeRef.findMethod(node.name, parameters)
        if (methods.length == 0) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          None
        } else if (methods.length > 1) {
          report(AMBIGUOUS_METHOD, node, node.name, typeNames(methods(0).arguments), typeNames(methods(1).arguments))
          None
        } else {
          val method = methods(0)
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          val methodSubst =
            GenericMethodTypeArguments.explicit(typing, node, method, node.typeArgs, classSubst) match {
              case Some(subst) => subst
              case None => return None
            }

          val expectedArgs = method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, methodSubst, defaultToBound = true))
          var i = 0
          while (i < parameters.length) {
            parameters(i) = processAssignable(node.args(i), expectedArgs(i), parameters(i))
            if (parameters(i) == null) return None
            i += 1
          }

          val call = new CallStatic(typeRef, method, parameters)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        }
      } else {
        val candidates = new JTreeSet[Method](new MethodComparator)

        def collectStatics(tp: ObjectType): Unit = {
          if (tp == null) return
          tp.methods(node.name).foreach { m =>
            if ((m.modifier & AST.M_STATIC) != 0) candidates.add(m)
          }
          collectStatics(tp.superClass)
          tp.interfaces.foreach(collectStatics)
        }

        collectStatics(typeRef)
        if (candidates.isEmpty) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          return None
        }

        final case class Applicable(method: Method, expectedArgs: Array[Type], methodSubst: scala.collection.immutable.Map[String, Type])

        val applicable = candidates.asScala.flatMap { method =>
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          val methodSubst = GenericMethodTypeArguments.infer(typing, node, method, parameters, classSubst, expected)
          val expectedArgs = method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, methodSubst, defaultToBound = true))
          if (expectedArgs.length != parameters.length) None
          else {
            var ok = true
            var i = 0
            while (i < expectedArgs.length && ok) {
              ok = TypeRules.isAssignable(expectedArgs(i), parameters(i).`type`)
              i += 1
            }
            if (ok) Some(Applicable(method, expectedArgs, methodSubst)) else None
          }
        }.toList

        if (applicable.isEmpty) {
          report(METHOD_NOT_FOUND, node, typeRef, node.name, types(parameters))
          None
        } else if (applicable.length == 1) {
          val selected = applicable.head
          val classSubst = TypeSubstitution.classSubstitution(typeRef)
          var i = 0
          while (i < parameters.length) {
            parameters(i) = processAssignable(node.args(i), selected.expectedArgs(i), parameters(i))
            if (parameters(i) == null) return None
            i += 1
          }
          val call = new CallStatic(typeRef, selected.method, parameters)
          val castType = TypeSubstitution.substituteType(selected.method.returnType, classSubst, selected.methodSubst, defaultToBound = true)
          Some(if (castType eq selected.method.returnType) call else new AsInstanceOf(call, castType))
        } else {
          def isAllSuperType(a: Array[Type], b: Array[Type]): Boolean =
            var i = 0
            while i < a.length do
              if !TypeRules.isSuperType(a(i), b(i)) then return false
              i += 1
            true

          val sorter: java.util.Comparator[Applicable] = new java.util.Comparator[Applicable] {
            def compare(a1: Applicable, a2: Applicable): Int =
              if isAllSuperType(a2.expectedArgs, a1.expectedArgs) then -1
              else if isAllSuperType(a1.expectedArgs, a2.expectedArgs) then 1
              else 0
          }

          val selected = new java.util.ArrayList[Applicable]()
          selected.addAll(applicable.asJava)
          java.util.Collections.sort(selected, sorter)
          if (selected.size < 2) {
            val only = selected.get(0)
            val classSubst = TypeSubstitution.classSubstitution(typeRef)
            var i = 0
            while (i < parameters.length) {
              parameters(i) = processAssignable(node.args(i), only.expectedArgs(i), parameters(i))
              if (parameters(i) == null) return None
              i += 1
            }
            val call = new CallStatic(typeRef, only.method, parameters)
            val castType = TypeSubstitution.substituteType(only.method.returnType, classSubst, only.methodSubst, defaultToBound = true)
            Some(if (castType eq only.method.returnType) call else new AsInstanceOf(call, castType))
          } else {
            val a1 = selected.get(0)
            val a2 = selected.get(1)
            if (sorter.compare(a1, a2) >= 0) {
              report(AMBIGUOUS_METHOD, node, node.name, typeNames(a1.method.arguments), typeNames(a2.method.arguments))
              None
            } else {
              val classSubst = TypeSubstitution.classSubstitution(typeRef)
              var i = 0
              while (i < parameters.length) {
                parameters(i) = processAssignable(node.args(i), a1.expectedArgs(i), parameters(i))
                if (parameters(i) == null) return None
                i += 1
              }
              val call = new CallStatic(typeRef, a1.method, parameters)
              val castType = TypeSubstitution.substituteType(a1.method.returnType, classSubst, a1.methodSubst, defaultToBound = true)
              Some(if (castType eq a1.method.returnType) call else new AsInstanceOf(call, castType))
            }
          }
        }
      }
    }
  }

  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null) return None
    val contextClass = definition_
    tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
      case Right(method) =>
        val classSubst = TypeSubstitution.classSubstitution(contextClass.superClass)
        val methodSubst =
          if (node.typeArgs.nonEmpty) {
            GenericMethodTypeArguments.explicit(typing, node, method, node.typeArgs, classSubst) match {
              case Some(subst) => subst
              case None => return None
            }
          } else {
            GenericMethodTypeArguments.infer(typing, node, method, parameters, classSubst, expected)
          }

        val expectedArgs = method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, methodSubst, defaultToBound = true))
        var i = 0
        while (i < parameters.length) {
          parameters(i) = processAssignable(node.args(i), expectedArgs(i), parameters(i))
          if (parameters(i) == null) return None
          i += 1
        }
        val call = new CallSuper(new This(contextClass), method, parameters)
        val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
        Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      case Left(_) =>
        report(METHOD_NOT_FOUND, node, contextClass, node.name, types(parameters))
        None
    }
  }

  def typeIndexing(node: AST.Indexing, context: LocalContext): Option[Term] = {
    val target = typed(node.lhs, context).getOrElse(null)
    val index = typed(node.rhs, context).getOrElse(null)
    if (target == null || index == null) return None

    if (target.isArrayType) {
      if (!(index.isBasicType && index.`type`.asInstanceOf[BasicType].isInteger)) {
        report(INCOMPATIBLE_TYPE, node, BasicType.INT, index.`type`)
        return None
      }
      Some(new RefArray(target, index))
    } else if (target.isBasicType) {
      report(INCOMPATIBLE_TYPE, node.lhs, rootClass, target.`type`)
      None
    } else {
      val params = Array(index)
      tryFindMethod(node, target.`type`.asInstanceOf[ObjectType], "get", Array[Term](index)) match {
        case Left(_) =>
          report(METHOD_NOT_FOUND, node, target.`type`, "get", types(params))
          None
        case Right(method) =>
          Some(new Call(target, method, params))
      }
    }
  }

  def typeNewArray(node: AST.NewArray, context: LocalContext): Option[Term] = {
    val typeRef = mapFrom(node.typeRef, mapper_)
    val parameters = typedTerms(node.args.toArray, context)
    if (typeRef == null || parameters == null) return None
    val resultType = loadArray(typeRef, parameters.length)
    Some(new NewArray(resultType, parameters))
  }

  def typeNewObject(node: AST.NewObject, context: LocalContext): Option[Term] = {
    val typeRef = mapFrom(node.typeRef).asInstanceOf[ClassType]
    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null || typeRef == null) return None
    val constructors = typeRef.findConstructor(parameters)
    if (constructors.length == 0) {
      report(CONSTRUCTOR_NOT_FOUND, node, typeRef, types(parameters))
      None
    } else if (constructors.length > 1) {
      report(
        AMBIGUOUS_CONSTRUCTOR,
        node,
        Array[AnyRef](constructors(0).affiliation, constructors(0).getArgs),
        Array[AnyRef](constructors(1).affiliation, constructors(1).getArgs)
      )
      None
    } else {
      typeRef match {
        case applied: TypedAST.AppliedClassType =>
          val appliedCtor = new TypedAST.ConstructorRef {
            def modifier: Int = constructors(0).modifier
            def affiliation: TypedAST.ClassType = applied
            def name: String = constructors(0).name
            def getArgs: Array[TypedAST.Type] = constructors(0).getArgs
          }
          Some(new NewObject(appliedCtor, parameters))
        case _ =>
          Some(new NewObject(constructors(0), parameters))
      }
    }
  }

  def typeStringInterpolation(node: AST.StringInterpolation, context: LocalContext): Option[Term] = {
    // Type check all interpolated expressions
    val typedExprs = node.expressions.map(e => typed(e, context).getOrElse(null))
    if (typedExprs.contains(null)) return None

    // Build string concatenation using StringBuilder
    val stringType = load("java.lang.String")
    val sbType = load("java.lang.StringBuilder")

    // Find StringBuilder no-arg constructor
    val constructors = sbType.findConstructor(Array[Term]())
    if (constructors.isEmpty) {
      report(SemanticError.CONSTRUCTOR_NOT_FOUND, node, sbType, Array[Type]())
      return None
    }
    val noArgConstructor = constructors(0)

    // Create StringBuilder
    val sb = new NewObject(noArgConstructor, Array[Term]())
    var result: Term = sb

    // Append parts and expressions
    val parts = node.parts
    for (i <- parts.indices) {
      if (parts(i).nonEmpty) {
        val part = new StringValue(node.location, parts(i), stringType)
        val appendMethods = sbType.findMethod("append", Array(part))
        if (appendMethods.nonEmpty) {
          result = new Call(result, appendMethods(0), Array(part))
        }
      }

      if (i < typedExprs.length) {
        val expr = typedExprs(i)
        // Try to find append method for the expression's type
        val appendMethods = sbType.findMethod("append", Array(expr))
        if (appendMethods.nonEmpty) {
          result = new Call(result, appendMethods(0), Array(expr))
        } else {
          // If no direct match, convert to string first
          val toStringMethods = expr.`type`.asInstanceOf[ObjectType].findMethod("toString", Array[Term]())
          if (toStringMethods.nonEmpty) {
            val stringExpr = new Call(expr, toStringMethods(0), Array[Term]())
            val appendStringMethods = sbType.findMethod("append", Array(stringExpr))
            if (appendStringMethods.nonEmpty) {
              result = new Call(result, appendStringMethods(0), Array(stringExpr))
            }
          }
        }
      }
    }

    // Call toString()
    val toStringMethods = sbType.findMethod("toString", Array[Term]())
    if (toStringMethods.isEmpty) {
      report(SemanticError.METHOD_NOT_FOUND, node, sbType, "toString", Array[Type]())
      return None
    }
    Some(new Call(result, toStringMethods(0), Array[Term]()))
  }

  def unimplementedBinaryAssignment(node: AST.Expression, lhs: AST.Expression, rhs: AST.Expression, context: LocalContext): Option[Term] = {
    typed(lhs, context)
    typed(rhs, context)
    report(UNIMPLEMENTED_FEATURE, node)
    None
  }

  def typeNumericBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) None else Option(processNumericExpression(kind, node, left, right))
  }

  def typeLogicalBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] = {
    val ops = processLogicalExpression(node, context)
    if (ops == null) None else Some(new BinaryTerm(kind, BasicType.BOOLEAN, ops(0), ops(1)))
  }

  def typeComparableBinary(node: AST.BinaryExpression, kind: Int, context: LocalContext): Option[Term] = {
    val ops = processComparableExpression(node, context)
    if (ops == null) None else Some(new BinaryTerm(kind, BasicType.BOOLEAN, ops(0), ops(1)))
  }

  def typeUnaryNumeric(node: AST.UnaryExpression, symbol: String, kind: Int, context: LocalContext): Option[Term] = {
    val term = typed(node.term, context).getOrElse(null)
    if (term == null) return None
    if (!hasNumericType(term)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
      None
    } else {
      Some(new UnaryTerm(kind, term.`type`, term))
    }
  }

  def typeUnaryBoolean(node: AST.UnaryExpression, symbol: String, kind: Int, context: LocalContext): Option[Term] = {
    val term = typed(node.term, context).getOrElse(null)
    if (term == null) return None
    if (term.`type` != BasicType.BOOLEAN) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](term.`type`))
      None
    } else {
      Some(new UnaryTerm(kind, BasicType.BOOLEAN, term))
    }
  }

  def typePostUpdate(node: AST.Expression, termNode: AST.Expression, symbol: String, binaryKind: Int, context: LocalContext): Option[Term] = {
    val operand = typed(termNode, context).getOrElse(null)
    if (operand == null) return None
    if ((!operand.isBasicType) || !hasNumericType(operand)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, symbol, Array[Type](operand.`type`))
      return None
    }
    Option(operand match {
      case ref: RefLocal =>
        termNode match {
          case id: AST.Id =>
            val bind = context.lookup(id.name)
            if (bind != null && !bind.isMutable) {
              report(CANNOT_ASSIGN_TO_VAL, id, id.name)
              return None
            }
          case _ =>
        }
        val varIndex = context.add(context.newName, operand.`type`)
        new Begin(
          new SetLocal(0, varIndex, operand.`type`, operand),
          new SetLocal(ref.frame, ref.index, ref.`type`, new BinaryTerm(binaryKind, operand.`type`, new RefLocal(0, varIndex, operand.`type`), new IntValue(1))),
          new RefLocal(0, varIndex, operand.`type`)
        )
      case ref: RefField =>
        if (Modifier.isFinal(ref.field.modifier)) {
          report(CANNOT_ASSIGN_TO_VAL, termNode, ref.field.name)
          return None
        }
        val varIndex = context.add(context.newName, ref.target.`type`)
        new Begin(
          new SetLocal(0, varIndex, ref.target.`type`, ref.target),
          new SetField(
            new RefLocal(0, varIndex, ref.target.`type`),
            ref.field,
            new BinaryTerm(binaryKind, operand.`type`, new RefField(new RefLocal(0, varIndex, ref.target.`type`), ref.field), new IntValue(1))
          )
        )
      case _ =>
        report(LVALUE_REQUIRED, termNode)
        null
    })
  }

  def typeAssignment(node: AST.Assignment, context: LocalContext): Option[Term] =
    node.lhs match {
      case _ : AST.Id =>
        Option(processLocalAssign(node, context))
      // Removed: UnqualifiedFieldReference case - use this.field or self.field instead
      case _ : AST.Indexing =>
        Option(processArrayAssign(node, context))
      case _ : AST.MemberSelection =>
        Option(processMemberAssign(node, context))
      case _ =>
        None
    }

  def typeElvis(node: AST.Elvis, context: LocalContext): Option[Term] = {
    val left = typed(node.lhs, context).getOrElse(null)
    val right = typed(node.rhs, context).getOrElse(null)
    if (left == null || right == null) return None
    if (left.isBasicType || right.isBasicType || !TypeRules.isAssignable(left.`type`, right.`type`)) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      None
    } else {
      Some(new BinaryTerm(ELVIS, left.`type`, left, right))
    }
  }

  def typeCast(node: AST.Cast, context: LocalContext): Option[Term] = {
    val term = typed(node.src, context).getOrElse(null)
    if (term == null) None
    else {
      val destination = mapFrom(node.to, mapper_)
      if (destination == null) None
      else Some(new AsInstanceOf(term, destination))
    }
  }

  def typeIsInstance(node: AST.IsInstance, context: LocalContext): Option[Term] = {
    val target = typed(node.target, context).getOrElse(null)
    val destinationType = mapFrom(node.typeRef, mapper_)
    if (target == null || destinationType == null) None
    else Some(new InstanceOf(target, destinationType))
  }

  def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] = node match {
    case node@AST.Addition(loc, _, _) =>
      var left = typed(node.lhs, context).getOrElse(null)
      var right = typed(node.rhs, context).getOrElse(null)
      if (left == null || right == null) return None
      if (left.isBasicType && right.isBasicType) {
        return Option(processNumericExpression(ADD, node, left, right))
      }
      if (left.isBasicType) {
        if (left.`type` == BasicType.VOID) {
          report(IS_NOT_BOXABLE_TYPE, node.lhs, left.`type`)
          return None
        } else {
          left = Boxing.boxing(table_, left)
        }
      }
      if (right.isBasicType) {
        if (right.`type` == BasicType.VOID) {
          report(IS_NOT_BOXABLE_TYPE, node.rhs, right.`type`)
          return None
        }
        else {
          right = Boxing.boxing(table_, right)
        }
      }
      val toStringL = findMethod(node.lhs, left.`type`.asInstanceOf[ObjectType], "toString")
      val toStringR = findMethod(node.rhs, right.`type`.asInstanceOf[ObjectType], "toString")
      left = new Call(left, toStringL, new Array[Term](0))
      right = new Call(right, toStringR, new Array[Term](0))
      val concat: Method = findMethod(node, left.`type`.asInstanceOf[ObjectType], "concat", Array[Term](right))
      Some(new Call(left, concat, Array[Term](right)))
    case node@AST.Subtraction(loc, left, right) =>
      typeNumericBinary(node, SUBTRACT, context)
    case node@AST.Multiplication(loc, left, right) =>
      typeNumericBinary(node, MULTIPLY, context)
    case node@AST.Division(loc, left, right) =>
      typeNumericBinary(node, DIVIDE, context)
    case node@AST.Modulo(loc, left, right) =>
      typeNumericBinary(node, MOD, context)
    case node: AST.Assignment =>
      typeAssignment(node, context)
    case node@AST.LogicalAnd(loc, left, right) =>
      typeLogicalBinary(node, LOGICAL_AND, context)
    case node@AST.LogicalOr(loc, left, right) =>
      typeLogicalBinary(node, LOGICAL_OR, context)
    case node@AST.BitAnd(loc, l, r) =>
      Option(processBitExpression(BIT_AND, node, context))
    case node@AST.BitOr(loc, l, r) =>
      Option(processBitExpression(BIT_OR, node, context))
    case node@AST.XOR(loc, left, right) =>
      Option(processBitExpression(XOR, node, context))
    case node@AST.LogicalRightShift(loc, left, right) =>
      Option(processShiftExpression(BIT_SHIFT_R3, node, context))
    case node@AST.MathLeftShift(loc, left, right) =>
      Option(processShiftExpression(BIT_SHIFT_L2, node, context))
    case node@AST.MathRightShift(loc, left, right) =>
      Option(processShiftExpression(BIT_SHIFT_R2, node, context))
    case node@AST.GreaterOrEqual(loc, left, right) =>
      typeComparableBinary(node, GREATER_OR_EQUAL, context)
    case node@AST.GreaterThan(loc, left, right) =>
      typeComparableBinary(node, GREATER_THAN, context)
    case node@AST.LessOrEqual(loc, left, right) =>
      typeComparableBinary(node, LESS_OR_EQUAL, context)
    case node@AST.LessThan(loc, left, right) =>
      typeComparableBinary(node, LESS_THAN, context)
    case node@AST.Equal(loc, left, right) =>
      Option(processEquals(EQUAL, node, context))
    case node@AST.NotEqual(loc, left, right) =>
      Option(processEquals(NOT_EQUAL, node, context))
    case node@AST.ReferenceEqual(loc, left, right) =>
      Option(processRefEquals(EQUAL, node, context))
    case node@AST.ReferenceNotEqual(loc, left, right) =>
      Option(processRefEquals(NOT_EQUAL, node, context))
    case node: AST.Elvis =>
      typeElvis(node, context)
    case node: AST.Indexing =>
      typeIndexing(node, context)
    case node@AST.AdditionAssignment(_, left, right) =>
      unimplementedBinaryAssignment(node, left, right, context)
    case node@AST.SubtractionAssignment(_, left, right) =>
      unimplementedBinaryAssignment(node, left, right, context)
    case node@AST.MultiplicationAssignment(_, left, right) =>
      unimplementedBinaryAssignment(node, left, right, context)
    case node@AST.DivisionAssignment(_, left, right) =>
      unimplementedBinaryAssignment(node, left, right, context)
    case node@AST.ModuloAssignment(_, left, right) =>
      unimplementedBinaryAssignment(node, left, right, context)
    case node@AST.CharacterLiteral(loc, v) =>
      Some(new CharacterValue(loc, v))
    case node@AST.IntegerLiteral(loc, v) =>
      Some(new IntValue(loc, v))
    case node@AST.LongLiteral(loc, v) =>
      Some(new LongValue(loc, v))
    case node@AST.FloatLiteral(loc, v) =>
      Some(new FloatValue(loc, v))
    case node@AST.DoubleLiteral(loc, v) =>
      Some(new DoubleValue(loc, v))
    case node@AST.BooleanLiteral(loc, v) =>
      Some(new BoolValue(loc, v))
    case node@AST.ListLiteral(loc, elements) =>
      Some(new ListLiteral(elements.map{e => typed(e, context).getOrElse(null)}.toArray, load("java.util.List")))
    case node@AST.NullLiteral(loc) =>
      Some(new NullValue(loc))
    case node: AST.Cast =>
      typeCast(node, context)
    case node@AST.ClosureExpression(loc, _, _, _, _, _) =>
      val args = node.args
      val name = node.mname
      openFrame(context){
        openClosure(context) {
          val argTypes = args.map{arg => addArgument(arg, context)}.toArray
          if (argTypes.exists(_ == null)) {
            None
          } else {
            val inferredTarget: ClassType =
              expected match {
                case ct: ClassType if node.typeRef.isRelaxed && ct.isInterface => ct
                case _ => null
              }

            val typeRef = Option(inferredTarget).getOrElse(mapFrom(node.typeRef).asInstanceOf[ClassType])
            if (typeRef == null) {
              None
            } else if (!typeRef.isInterface) {
              report(INTERFACE_REQUIRED, node.typeRef, typeRef)
              None
            } else {
              val classSubst = TypeSubstitution.classSubstitution(typeRef)

              def substitutedArgs(method: Method): Array[Type] =
                method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true))

              def substitutedReturn(method: Method): Type =
                TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)

              val candidates = typeRef.methods.filter(m => m.name == name && m.arguments.length == argTypes.length)
              val method = candidates.find(m => sameTypes(substitutedArgs(m), argTypes)).getOrElse(null)
              if (method == null) {
                report(METHOD_NOT_FOUND, node, typeRef, name, argTypes)
                None
              } else {
                val expectedArgs = substitutedArgs(method)
                val expectedRet = substitutedReturn(method)

                val typedMethod = new Method {
                  def modifier: Int = method.modifier
                  def affiliation: ClassType = method.affiliation
                  def name: String = method.name
                  override def arguments: Array[Type] = expectedArgs.clone()
                  override def returnType: Type = expectedRet
                  override def typeParameters: Array[TypedAST.TypeParameter] = Array()
                }

                context.setMethod(typedMethod)
                context.getContextFrame.parent.setAllClosed(true)

                val prologue = Buffer[ActionStatement]()
                var i = 0
                while (i < args.length) {
                  val bind = context.lookup(args(i).name)
                  if (bind != null) {
                    val erased =
                      TypeSubstitution.substituteType(
                        method.arguments(i),
                        scala.collection.immutable.Map.empty,
                        scala.collection.immutable.Map.empty,
                        defaultToBound = true
                      )
                    val desired = expectedArgs(i)
                    if (desired ne erased) {
                      val rawBind = new ClosureLocalBinding(bind.frameIndex, bind.index, erased, bind.isMutable)
                      val casted = new AsInstanceOf(new RefLocal(rawBind), desired)
                      prologue += new ExpressionActionStatement(new SetLocal(bind, casted))
                    }
                  }
                  i += 1
                }

                var block: ActionStatement = translate(node.body, context)
                if (prologue.nonEmpty) {
                  block = new StatementBlock((prologue.toIndexedSeq :+ block).asJava)
                }

                block = addReturnNode(block, expectedRet)
                val result = new NewClosure(typeRef, method, block)
                result.frame_=(context.getContextFrame)
                Some(result)
              }
            }
          }
        }
      }
    case node@AST.CurrentInstance(loc) =>
      if(context.isStatic) None else Some(new This(loc, definition_))
    case node@AST.Id(loc, name) =>
      val bind = context.lookup(name)
      if (bind == null) {
        report(VARIABLE_NOT_FOUND, node, node.name)
        None
      }else {
        Some(new RefLocal(bind))
      }
    case node: AST.UnqualifiedFieldReference =>
      report(UNIMPLEMENTED_FEATURE, node)
      None
    case node: AST.IsInstance =>
      typeIsInstance(node, context)
    case node: AST.MemberSelection =>
      typeMemberSelection(node, context)
    case node: AST.MethodCall =>
      typeMethodCall(node, context, expected)
    case node@AST.Negate(loc, target) =>
      typeUnaryNumeric(node, "-", MINUS, context)
    case node: AST.NewArray =>
      typeNewArray(node, context)
    case node: AST.NewObject =>
      typeNewObject(node, context)
    case node@AST.Not(loc, target) =>
      typeUnaryBoolean(node, "!", NOT, context)
    case node@AST.Posit(loc, target) =>
      typeUnaryNumeric(node, "+", PLUS, context)
    case node@AST.PostDecrement(loc, target) =>
      typePostUpdate(node, node.term, "--", SUBTRACT, context)
    case node@AST.PostIncrement(loc, target) =>
      typePostUpdate(node, node.term, "++", ADD, context)
    // Removed: UnqualifiedFieldReference - use this.field or self.field instead
    case node: AST.UnqualifiedMethodCall =>
      typeUnqualifiedMethodCall(node, context, expected)
    case node: AST.StaticMemberSelection =>
      typeStaticMemberSelection(node)
    case node: AST.StaticMethodCall =>
      typeStaticMethodCall(node, context, expected)
    case node@AST.StringLiteral(loc, value) =>
      Some(new StringValue(loc, value, load("java.lang.String")))
    case node: AST.StringInterpolation =>
      typeStringInterpolation(node, context)
    case node: AST.SuperMethodCall =>
      typeSuperMethodCall(node, context, expected)
  }
  def translate(node: AST.CompoundExpression, context: LocalContext): ActionStatement = node match {
    case AST.BlockExpression(loc, elements) =>
      context.openScope {
        new StatementBlock(elements.map(e => translate(e, context)).toIndexedSeq*)
      }
    case node@AST.BreakExpression(loc) =>
      new Break(loc)
    case node@AST.ContinueExpression(loc) =>
      new Continue(loc)
    case node@AST.EmptyExpression(loc) =>
      new NOP(loc)
    case node@AST.ExpressionBox(loc, body) =>
      typed(body, context).map{e =>  new ExpressionActionStatement(loc, e)}.getOrElse(new NOP(loc))
    case node@AST.ForeachExpression(loc, _, _, _) =>
      context.openScope {
        val collection = typed(node.collection, context).getOrElse(null)
        val arg = node.arg
        addArgument(arg, context)
        var block = translate(node.statement, context)
        if (collection == null) {
          new NOP(node.location)
        } else if (collection.isBasicType) {
          report(INCOMPATIBLE_TYPE, node.collection, load("java.util.Collection"), collection.`type`)
          new NOP(node.location)
        } else {
          val elementVar = context.lookupOnlyCurrentScope(arg.name)
          val collectionVar = new ClosureLocalBinding(0, context.add(context.newName, collection.`type`), collection.`type`, isMutable = true)

          if (collection.isArrayType) {
            val counterVariable = new ClosureLocalBinding(0, context.add(context.newName, BasicType.INT), BasicType.INT, isMutable = true)
            val init =
              new StatementBlock(
                new ExpressionActionStatement(new SetLocal(collectionVar, collection)),
                new ExpressionActionStatement(new SetLocal(counterVariable, new IntValue(0)))
              )

            block =
              new ConditionalLoop(
                new BinaryTerm(LESS_THAN, BasicType.BOOLEAN, ref(counterVariable), new ArrayLength(ref(collectionVar))),
                new StatementBlock(
                  assign(elementVar, indexref(collectionVar, ref(counterVariable))),
                  block,
                  assign(counterVariable, new BinaryTerm(ADD, BasicType.INT, ref(counterVariable), new IntValue(1)))
                )
              )
            new StatementBlock(init, block)
          } else {
            val iteratorType = load("java.util.Iterator")
            val iteratorVar = new ClosureLocalBinding(0, context.add(context.newName, iteratorType), iteratorType, isMutable = true)
            val mIterator = findMethod(node.collection, collection.`type`.asInstanceOf[ObjectType], "iterator")
            val mNext = findMethod(node.collection, iteratorType, "next")
            val mHasNext = findMethod(node.collection, iteratorType, "hasNext")
            val init =
              new StatementBlock(
                new ExpressionActionStatement(new SetLocal(collectionVar, collection)),
                assign(iteratorVar, new Call(ref(collectionVar), mIterator, new Array[Term](0)))
              )
            var next: Term = new Call(ref(iteratorVar), mNext, new Array[Term](0))
            if (elementVar.tp != rootClass) {
              next = new AsInstanceOf(next, elementVar.tp)
            }
            block = new ConditionalLoop(new Call(ref(iteratorVar), mHasNext, new Array[Term](0)), new StatementBlock(assign(elementVar, next), block))
            new StatementBlock(init, block)
          }
        }
      }
    case node@AST.ForExpression(loc, _, _, _, _) =>
      context.openScope {
        val init = Option(node.init).map{init => translate(init, context)}.getOrElse(new NOP(loc))
        val condition = (for(c <- Option(node.condition)) yield {
          val conditionOpt = typed(c, context)
          val expected = BasicType.BOOLEAN
          for(condition <- conditionOpt; if condition.`type` != expected) {
            report(INCOMPATIBLE_TYPE, node.condition, condition.`type`, expected)
          }
          conditionOpt.getOrElse(null)
        }).getOrElse(new BoolValue(loc, true))
        val update = Option(node.update).flatMap{update => typed(update, context)}.getOrElse(null)
        var loop = translate(node.block, context)
        if(update != null) loop = new StatementBlock(loop, new ExpressionActionStatement(update))
        new StatementBlock(init.location, init, new ConditionalLoop(condition, loop))
      }
    case node@AST.IfExpression(loc, _, _, _) =>
      context.openScope {
        val conditionOpt = typed(node.condition, context)
        val expected = BasicType.BOOLEAN
        for(condition <- conditionOpt if condition.`type` != expected) {
          report(INCOMPATIBLE_TYPE, node.condition, expected, condition.`type`)
        }
        val thenBlock = translate(node.thenBlock, context)
        val elseBlock = if (node.elseBlock == null) null else translate(node.elseBlock, context)
        conditionOpt.map{c => new IfStatement(c, thenBlock, elseBlock)}.getOrElse(new NOP(loc))
      }
    case node@AST.LocalVariableDeclaration(loc, modifiers, name, typeRef, init) =>
      val binding = context.lookupOnlyCurrentScope(name)
      if (binding != null) {
        report(DUPLICATE_LOCAL_VARIABLE, node, name)
        return new NOP(loc)
      }
      if (typeRef == null) {
        val inferred = typed(init, context).getOrElse(null)
        if (inferred == null) return new NOP(loc)
        val inferredType = inferred.`type`
        if (inferredType == BasicType.VOID) {
          report(INCOMPATIBLE_TYPE, init, rootClass, inferredType)
          return new NOP(loc)
        }
        val index = context.add(name, inferredType, isMutable = !Modifier.isFinal(modifiers))
        new ExpressionActionStatement(new SetLocal(loc, 0, index, inferredType, inferred))
      } else {
        val lhsType = mapFrom(node.typeRef)
        if (lhsType == null) return new NOP(loc)
        val index = context.add(name, lhsType, isMutable = !Modifier.isFinal(modifiers))
        var local: SetLocal = null
        if (init != null) {
          val valueNode = typed(init, context, lhsType)
          valueNode match {
            case None => return new NOP(loc)
            case Some(v) =>
              val value = processAssignable(init, lhsType, v)
              if (value == null) return new NOP(loc)
              local = new SetLocal(loc, 0, index, lhsType, value)
          }
        }
        else {
          local = new SetLocal(loc, 0, index, lhsType, defaultValue(lhsType))
        }
        new ExpressionActionStatement(local)
      }
    case node@AST.ReturnExpression(loc, _) =>
      val returnType = context.returnType
      if(node.result == null) {
        val expected  = BasicType.VOID
        if (returnType != expected) report(CANNOT_RETURN_VALUE, node)
        new Return(loc, null)
      } else {
        typed(node.result, context, returnType) match {
          case None =>
            new Return(loc, null)
          case Some(returned) if returned.`type` == BasicType.VOID =>
            report(CANNOT_RETURN_VALUE, node)
            new Return(loc, null)
          case Some(returned) =>
            val value = processAssignable(node.result, returnType, returned)
            if (value == null) new Return(loc, null) else new Return(loc, value)
        }
      }
    case node@AST.SelectExpression(loc, _, _, _) =>
      val conditionOpt = typed(node.condition, context)
      if(conditionOpt == None) return new NOP(loc)
      val condition = conditionOpt.get
      val name = context.newName
      val index = context.add(name, condition.`type`)
      val statement = if(node.cases.length == 0) {
        Option(node.elseBlock).map{e => translate(e, context)}.getOrElse(new NOP(loc))
      }else {
        val cases = node.cases
        val nodes = Buffer[Term]()
        val thens = Buffer[ActionStatement]()
        for((expressions, thenClause)<- cases) {
          val bind = context.lookup(name)
          nodes += processNodes(expressions.toArray, condition.`type`, bind, context)
          thens += translate(thenClause, context)
        }
        var branches: ActionStatement = if(node.elseBlock != null) {
          translate(node.elseBlock, context)
        }else {
          null
        }
        for(i <- (cases.length - 1) to (0, -1)) {
          branches = new IfStatement(nodes(i), thens(i), branches)
        }
        branches
      }
      new StatementBlock(condition.location, new ExpressionActionStatement(condition.location, new SetLocal(0, index, condition.`type`, condition)), statement)
    case node@AST.SynchronizedExpression(loc, _, _) =>
      context.openScope {
        val lock = typed(node.condition, context).getOrElse(null)
        val block = translate(node.block, context)
        report(UNIMPLEMENTED_FEATURE, node)
        new Synchronized(node.location, lock, block)
      }
    case node@AST.ThrowExpression(loc, target) =>
      val expressionOpt = typed(target, context)
      for(expression <- expressionOpt) {
        val expected = load("java.lang.Throwable")
        val detected = expression.`type`
        if (!TypeRules.isSuperType(expected, detected)) {
          report(INCOMPATIBLE_TYPE, node, expected, detected)
        }
      }
      new Throw(loc, expressionOpt.getOrElse(null))
    case node@AST.TryExpression(loc, tryBlock, recClauses, finBlock) =>
      val tryStatement = translate(tryBlock, context)
      val binds = new Array[ClosureLocalBinding](recClauses.length)
      val catchBlocks = new Array[ActionStatement](recClauses.length)
      for(i <- 0 until recClauses.length) {
        val (argument, body) = recClauses(i)
        context.openScope {
          val argType = addArgument(argument, context)
          val expected = load("java.lang.Throwable")
          if (!TypeRules.isSuperType(expected, argType)) {
            report(INCOMPATIBLE_TYPE, argument, expected, argType)
          }
          binds(i) = context.lookupOnlyCurrentScope(argument.name)
          catchBlocks(i) = translate(body, context)
        }
      }
      new Try(loc, tryStatement, binds, catchBlocks)
    case node@AST.WhileExpression(loc, _, _) =>
      context.openScope {
        val conditionOpt = typed(node.condition, context)
        val expected = BasicType.BOOLEAN
        for(condition <- conditionOpt) {
          val actual = condition.`type`
          if(actual != expected)  report(INCOMPATIBLE_TYPE, node, expected, actual)
        }
        val thenBlock = translate(node.block, context)
        new ConditionalLoop(loc, conditionOpt.getOrElse(null), thenBlock)
      }
  }
  def defaultValue(typeRef: Type): Term = Term.defaultValue(typeRef)
  def addReturnNode(node: ActionStatement, returnType: Type): StatementBlock = {
    new StatementBlock(node, new Return(defaultValue(returnType)))
  }
  def createMain(top: ClassType, ref: Method, name: String, args: Array[Type], ret: Type): MethodDefinition = {
    val method = new MethodDefinition(null, AST.M_STATIC | AST.M_PUBLIC, top, name, args, ret, null)
    val frame = new LocalFrame(null)
    val params = new Array[Term](args.length)
    for(i <- 0 until args.length) {
      val arg = args(i)
      val index = frame.add("args" + i, arg)
      params(i) = new RefLocal(0, index, arg)
    }
    method.setFrame(frame)
    val constructor = top.findConstructor(new Array[Term](0))(0)
    var block = new StatementBlock(new ExpressionActionStatement(new Call(new NewObject(constructor, new Array[Term](0)), ref, params)))
    block = addReturnNode(block, BasicType.VOID)
    method.setBlock(block)
    method
  }

  private def createEquals(kind: Int, lhs: Term, rhs: Term): Term = {
    val params = Array[Term](new AsInstanceOf(rhs, rootClass))
    val target = lhs.`type`.asInstanceOf[ObjectType]
    val methods = target.findMethod("equals", params)
    var node: Term = new Call(lhs, methods(0), params)
    if (kind == BinaryTerm.Constants.NOT_EQUAL) {
      node = new UnaryTerm(NOT, BasicType.BOOLEAN, node)
    }
    node
  }
  private def indexref(bind: ClosureLocalBinding, value: Term): Term =
    new RefArray(new RefLocal(bind), value)
  private def assign(bind: ClosureLocalBinding, value: Term): ActionStatement =
    new ExpressionActionStatement(new SetLocal(bind, value))
  private def ref(bind: ClosureLocalBinding): Term =
    new RefLocal(bind)
  private def findMethod(node: AST.Node, target: ObjectType, name: String): Method =
    findMethod(node, target, name, new Array[Term](0))
  private def findMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Method = {
    val methods = MethodResolution.findMethods(target, name, params)
    if (methods.length == 0) {
      report(METHOD_NOT_FOUND, node, target, name, params.map{param => param.`type`})
      return null
    }
    methods(0)
  }
  private def hasNumericType(term: Term): Boolean = numeric(term.`type`)
  private def numeric(symbol: Type): Boolean = {
    symbol.isBasicType && (symbol == BasicType.BYTE || symbol == BasicType.SHORT || symbol == BasicType.CHAR || symbol == BasicType.INT || symbol == BasicType.LONG || symbol == BasicType.FLOAT || symbol == BasicType.DOUBLE)
  }
  private def types(terms: Array[Term]): Array[Type] = terms.map(term => term.`type`)
  private def typeNames(types: Array[Type]): Array[String] = types.map(_.name)
  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Continuable, Method] = {
    val methods = MethodResolution.findMethods(target, name, params)
    if (methods.length > 0) {
      if (methods.length > 1) {
        report(AMBIGUOUS_METHOD, node, Array[AnyRef](methods(0).affiliation, name, methods(0).arguments), Array[AnyRef](methods(1).affiliation, name, methods(1).arguments))
        Left(false)
      } else if (!MemberAccess.isMemberAccessible(methods(0), definition_)) {
        report(METHOD_NOT_ACCESSIBLE, node, methods(0).affiliation, name, methods(0).arguments, definition_)
        Left(false)
      } else {
        Right(methods(0))
      }
    }else {
      Left(true)
    }
  }

  private def sameTypes(left: Array[Type], right: Array[Type]): Boolean = {
    if (left.length != right.length) return false
    (for (i <- 0 until left.length) yield (left(i), right(i))).forall { case (l, r) => l eq r }
  }
  private def getter(name: String): String =
    "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private def getterBoolean(name: String): String =
    "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private def setter(name: String): String =
    "set" + Character.toUpperCase(name.charAt(0)) + name.substring(1)
  private def promote(left: Type, right: Type): Type = {
    if (!numeric(left) || !numeric(right)) return null
    if ((left eq BasicType.DOUBLE) || (right eq BasicType.DOUBLE)) {
      return BasicType.DOUBLE
    }
    if ((left eq BasicType.FLOAT) || (right eq BasicType.FLOAT)) {
      return BasicType.FLOAT
    }
    if ((left eq BasicType.LONG) || (right eq BasicType.LONG)) {
      return BasicType.LONG
    }
    BasicType.INT
  }
  private def processNumericExpression(kind: Int, node: AST.BinaryExpression, lt: Term, rt: Term): Term = {
    var left = lt
    var right = rt
    if ((!hasNumericType(left)) || (!hasNumericType(right))) {
      report(INCOMPATIBLE_OPERAND_TYPE, node, node.symbol, Array[Type](left.`type`, right.`type`))
      return null
    }
    val resultType = promote(left.`type`, right.`type`)
    if (left.`type` != resultType) left = new AsInstanceOf(left, resultType)
    if (right.`type` != resultType) right = new AsInstanceOf(right, resultType)
    new BinaryTerm(kind, resultType, left, right)
  }
  private def promoteInteger(typeRef: Type): Type = {
    if (typeRef == BasicType.BYTE || typeRef == BasicType.SHORT || typeRef == BasicType.CHAR || typeRef == BasicType.INT) {
      return BasicType.INT
    }
    if (typeRef == BasicType.LONG) {
      return BasicType.LONG
    }
    null
  }
  private def addArgument(arg: AST.Argument, context: LocalContext): Type = {
    val name = arg.name
    val binding = context.lookupOnlyCurrentScope(name)
    if (binding != null) {
      report(DUPLICATE_LOCAL_VARIABLE, arg, name)
      return null
    }
    val argType = mapFrom(arg.typeRef, mapper_)
    if(argType == null) return null
    context.add(name, argType)
    argType
  }

  private def runUnit(): Unit = {
    unit_ = unit
    val toplevels = unit.toplevels
    val context = new LocalContext
    val statements = Buffer[ActionStatement]()
    mapper_ = find(topClass)
    val klass = loadTopClass.asInstanceOf[ClassDefinition]
    val argsType = loadArray(load("java.lang.String"), 1)
    val method = new MethodDefinition(unit.location, AST.M_PUBLIC, klass, "start", Array[Type](argsType), BasicType.VOID, null)
    context.add("args", argsType)
    for (element <- toplevels) {
      if (!element.isInstanceOf[AST.TypeDeclaration]) definition_ = klass
      element match {
        case node: AST.CompoundExpression =>
          context.setMethod(method)
          statements += translate(node, context)
        case _ =>
          element match {
            case node: AST.ClassDeclaration => processClassDeclaration(node, context)
            case node: AST.InterfaceDeclaration => processInterfaceDeclaration(node, context)
            case node: AST.FunctionDeclaration => processFunctionDeclaration(node, context)
            case node: AST.GlobalVariableDeclaration => processGlobalVariableDeclaration(node, context)
            case _ =>
          }
      }
    }
    if (klass != null) {
      statements += new Return(null)
      method.setBlock(new StatementBlock(statements.asJava))
      method.setFrame(context.getContextFrame)
      klass.add(method)
      klass.add(createMain(klass, method, "main", Array[Type](argsType), BasicType.VOID))
    }
  }
}
