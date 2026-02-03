package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

import scala.jdk.CollectionConverters.*

final class ClosureTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  def typeClosure(node: AST.ClosureExpression, context: LocalContext, expected: Type): Option[Term] = {
    val args = node.args
    val name = node.mname
    val inferredTarget: ClassType =
      expected match {
        case ct: ClassType if node.typeRef.isRelaxed && ct.isInterface => ct
        case _ => null
      }

    val rawTypeRef = Option(inferredTarget).getOrElse(mapFrom(node.typeRef).asInstanceOf[ClassType])
    if (rawTypeRef == null) {
      None
    } else if (!rawTypeRef.isInterface) {
      report(INTERFACE_REQUIRED, node.typeRef, rawTypeRef)
      None
    } else {
      val argTypesOpt = resolveClosureArgTypes(node, inferredTarget)
      if (argTypesOpt.isEmpty) return None
      val argTypes = argTypesOpt.get
      val hasReturn = containsReturn(node.body)
      val useExpressionBody = !hasReturn

      // Check if inferredTarget has a return type that needs inference
      // This happens when the expected type is like Function1<String, Object> where U was bound to Object
      // or Function1<String, Future<U>> where U is a type variable inside a complex type
      val needsReturnTypeInference = inferredTarget match {
        case applied: AppliedClassType =>
          // Check if the last type argument (return type for FunctionN) contains type variables
          applied.typeArguments.lastOption.exists { lastArg =>
            lastArg == rootClass || containsTypeVariable(lastArg)
          }
        case _ => false
      }

      val inferredReturnType =
        if ((inferredTarget == null || needsReturnTypeInference) && node.typeRef.isRelaxed) {
          if (useExpressionBody) inferReturnTypeFromExpressionBody(node, context, argTypes)
          else inferReturnTypeFromReturns(node, context, argTypes)
        } else None

      val typeRef =
        if (inferredTarget != null && inferredReturnType.isDefined) {
          // Use inferred return type to create a more precise type
          inferFunctionType(rawTypeRef, argTypes, inferredReturnType)
        } else if (inferredTarget != null) {
          rawTypeRef
        } else if (node.typeRef.isRelaxed) {
          inferFunctionType(rawTypeRef, argTypes, inferredReturnType)
        } else {
          rawTypeRef
        }
      val classSubst = TypeSubstitution.classSubstitution(typeRef)

      def substitutedArgs(method: Method): Array[Type] =
        method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true))

      // ラムダの期待戻り値型を計算する際、残った型変数（外側メソッドの型パラメータ等）は
      // rootClassに置換する。これにより Function1<String, Future<U>> の U が Object になっても
      // ラムダ本体が Future<String> を返すとき、戻り値型チェックが通るようになる。
      def substitutedReturn(method: Method): Type =
        TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)

      val candidates = typeRef.methods.filter(m => m.name == name && m.arguments.length == argTypes.length)
      candidates.find(m => sameTypes(substitutedArgs(m), argTypes)) match {
        case None =>
          report(METHOD_NOT_FOUND, node, typeRef, name, argTypes)
          None
        case Some(method) =>
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

          body.openFrame(context) {
            body.openClosure(context) {
              if (!addClosureArguments(args, argTypes, context)) {
                None
              } else {
                context.setMethod(typedMethod)
                context.getContextFrame.parent.setAllClosed(true)

                val prologue = args.zipWithIndex.flatMap { case (arg, i) =>
                  Option(context.lookup(arg.name)).flatMap { bind =>
                    val erased = TypeSubstitution.substituteType(
                      typedMethod.arguments(i), Map.empty, Map.empty, defaultToBound = true
                    )
                    val desired = expectedArgs(i)
                    Option.when(desired ne erased) {
                      val rawBind = new ClosureLocalBinding(bind.frameIndex, bind.index, erased, bind.isMutable)
                      val casted = new AsInstanceOf(new RefLocal(rawBind), desired)
                      new ExpressionActionStatement(new SetLocal(bind, casted))
                    }
                  }
                }

                val baseBlockOpt =
                  if (useExpressionBody) {
                    body.typed(node.body, context).flatMap { bodyTerm =>
                      Option(buildReturnBlock(node.body, bodyTerm, expectedRet))
                    }
                  } else {
                    Option(body.translate(node.body, context))
                  }
                if (baseBlockOpt.isEmpty) {
                  None
                } else {
                  val baseBlock = baseBlockOpt.get
                  val block = if (prologue.nonEmpty)
                    new StatementBlock((prologue :+ baseBlock).asJava)
                  else baseBlock

                  val finalBlock =
                    if (useExpressionBody) block
                    else body.addReturnNode(block, expectedRet)
                  val result = new NewClosure(typeRef, typedMethod, finalBlock)
                  result.frame_=(context.getContextFrame)
                  Some(result)
                }
              }
            }
          }
      }
    }
  }

  private def resolveClosureArgTypes(
    node: AST.ClosureExpression,
    inferredTarget: ClassType
  ): Option[Array[Type]] = {
    val args = node.args
    val argTypes = new Array[Type](args.length)
    var i = 0
    var hasMissing = false
    while (i < args.length) {
      val arg = args(i)
      if (arg.typeRef == null) {
        argTypes(i) = null
        hasMissing = true
      } else {
        val mapped = mapFrom(arg.typeRef)
        if (mapped == null) return None
        argTypes(i) = mapped
      }
      i += 1
    }

    if (!hasMissing) {
      Some(argTypes)
    } else if (inferredTarget == null) {
      args.zipWithIndex.foreach { case (arg, idx) =>
        if (arg.typeRef == null) report(LAMBDA_PARAM_TYPE_REQUIRED, arg, arg.name)
      }
      None
    } else {
      expectedMethodArgs(inferredTarget, node.mname, args.length) match {
        case None =>
          None
        case Some(expectedArgs) =>
          i = 0
          while (i < argTypes.length) {
            if (argTypes(i) == null) argTypes(i) = expectedArgs(i)
            i += 1
          }
          Some(argTypes)
      }
    }
  }

  private def expectedMethodArgs(target: ClassType, name: String, arity: Int): Option[Array[Type]] = {
    val methodOpt = target.methods.find(m => m.name == name && m.arguments.length == arity)
    methodOpt.map { method =>
      val classSubst = TypeSubstitution.classSubstitution(target)
      method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true))
    }
  }

  private def addClosureArguments(
    args: List[AST.Argument],
    argTypes: Array[Type],
    context: LocalContext
  ): Boolean = {
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (context.lookupOnlyCurrentScope(arg.name) != null) {
        report(DUPLICATE_LOCAL_VARIABLE, arg, arg.name)
        return false
      }
      val argType = argTypes(i)
      if (argType == null) return false
      context.add(arg.name, argType)
      i += 1
    }
    true
  }

  private def sameTypes(left: Array[Type], right: Array[Type]): Boolean =
    TypeCheckingHelpers.sameTypes(left, right)

  private def inferFunctionType(
    rawType: ClassType,
    argTypes: Array[Type],
    inferredReturnType: Option[Type]
  ): ClassType = {
    if (inferredReturnType.isEmpty && !rawType.isInterface) return rawType

    // Get the underlying interface type (unwrap AppliedClassType if necessary)
    val baseType = rawType match {
      case applied: AppliedClassType => applied.raw
      case other => other
    }

    val paramCount = baseType.typeParameters.length
    if (paramCount == argTypes.length + 1) {
      val ret = inferredReturnType.getOrElse(rootClass)
      AppliedClassType(baseType, (argTypes.toIndexedSeq :+ ret).toList)
    } else {
      rawType
    }
  }

  private def inferReturnTypeFromExpressionBody(
    node: AST.ClosureExpression,
    context: LocalContext,
    argTypes: Array[Type]
  ): Option[Type] = {
    var result: Option[Type] = None
    typing.withSuppressedReporting {
      body.openFrame(context) {
        body.openClosure(context) {
          if (addClosureArguments(node.args, argTypes, context)) {
            result = body.typed(node.body, context).map(_.`type`)
          }
        }
      }
    }
    result
  }

  private def inferReturnTypeFromReturns(
    node: AST.ClosureExpression,
    context: LocalContext,
    argTypes: Array[Type]
  ): Option[Type] = {
    var result: Option[Type] = None
    typing.withSuppressedReporting {
      body.openFrame(context) {
        body.openClosure(context) {
          if (addClosureArguments(node.args, argTypes, context)) {
            val collected = context.startReturnTypeCollection()
            val translated = body.translate(node.body, context)
            context.stopReturnTypeCollection()
            if (translated != null) {
              result = foldReturnLub(node, collected.toSeq)
            }
          }
        }
      }
    }
    result
  }

  private def foldReturnLub(node: AST.Node, types: Seq[Type]): Option[Type] = {
    val baseTypes = types.filterNot(_.isBottomType)
    val nonVoid = baseTypes.filter(_ != BasicType.VOID)
    val candidates = if (nonVoid.nonEmpty) nonVoid else baseTypes
    if (candidates.isEmpty) {
      Some(BasicType.VOID)
    } else {
      var current = candidates.head
      var i = 1
      while (i < candidates.length) {
        val next = candidates(i)
        val result = leastUpperBound(node, current, next)
        if (result == null) return None
        current = result
        i += 1
      }
      Some(current)
    }
  }

  private def leastUpperBound(node: AST.Node, left: Type, right: Type): Type =
    TypeCheckingHelpers.leastUpperBound(node, left, right, rootClass,
      (n, l, r) => report(INCOMPATIBLE_TYPE, n, l, r))

  private def buildReturnBlock(node: AST.BlockExpression, bodyTerm: Term, returnType: Type): ActionStatement = {
    val terms = bodyTerm match {
      case begin: Begin => begin.terms.toIndexedSeq
      case single => IndexedSeq(single)
    }
    if (terms.isEmpty) {
      return new StatementBlock(node.location, new Return(defaultValue(returnType)))
    }

    val statements = scala.collection.mutable.ArrayBuffer[ActionStatement]()
    var i = 0
    while (i < terms.length - 1) {
      statements += termToStatement(terms(i))
      i += 1
    }

    val last = terms.last
    val lastStatement =
      if (returnType == BasicType.VOID || returnType.isBottomType) {
        val stmt = termToStatement(last)
        statements += stmt
        new Return(defaultValue(returnType))
      } else {
        last match {
          case st: StatementTerm =>
            val assigned = processAssignable(node, returnType, st)
            if (assigned == null) return null
            new Return(assigned)
          case _ =>
            val assigned = processAssignable(node, returnType, last)
            if (assigned == null) return null
            new Return(assigned)
        }
      }

    statements += lastStatement
    new StatementBlock(statements.asJava)
  }

  private def termToStatement(term: Term): ActionStatement = term match {
    case st: StatementTerm => st.statement
    case other => new ExpressionActionStatement(other)
  }

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def defaultValue(tp: Type): Term =
    body.defaultValue(tp)

  /** Recursively check if a type contains any type variables */
  private def containsTypeVariable(typ: Type): Boolean =
    TypeCheckingHelpers.containsTypeVariable(typ)

  private def containsReturn(node: AST.Node): Boolean = {
    var found = false

    def visitChildren(n: AST.Node)(visit: AST.Node => Unit): Unit = n match {
      case block: AST.BlockExpression =>
        block.elements.foreach(visit)

      case ifExpr: AST.IfExpression =>
        visit(ifExpr.condition)
        visit(ifExpr.thenBlock)
        if (ifExpr.elseBlock != null) visit(ifExpr.elseBlock)

      case whileExpr: AST.WhileExpression =>
        visit(whileExpr.condition)
        visit(whileExpr.block)

      case foreachExpr: AST.ForeachExpression =>
        visit(foreachExpr.collection)
        visit(foreachExpr.statement)

      case forExpr: AST.ForExpression =>
        if (forExpr.init != null) visit(forExpr.init)
        if (forExpr.condition != null) visit(forExpr.condition)
        if (forExpr.update != null) visit(forExpr.update)
        visit(forExpr.block)

      case assign: AST.Assignment =>
        visit(assign.lhs)
        visit(assign.rhs)

      case localVar: AST.LocalVariableDeclaration =>
        if (localVar.init != null) visit(localVar.init)

      case binary: AST.BinaryExpression =>
        visit(binary.lhs)
        visit(binary.rhs)

      case unary: AST.UnaryExpression =>
        visit(unary.term)

      case call: AST.MethodCall =>
        if (call.target != null) visit(call.target)
        call.args.foreach(visit)

      case call: AST.UnqualifiedMethodCall =>
        call.args.foreach(visit)

      case call: AST.StaticMethodCall =>
        call.args.foreach(visit)

      case call: AST.SuperMethodCall =>
        call.args.foreach(visit)

      case newObj: AST.NewObject =>
        newObj.args.foreach(visit)

      case newArray: AST.NewArray =>
        newArray.args.foreach(visit)

      case newArrayWithValues: AST.NewArrayWithValues =>
        newArrayWithValues.values.foreach(visit)

      case listLit: AST.ListLiteral =>
        listLit.elements.foreach(visit)

      case cast: AST.Cast =>
        visit(cast.src)

      case isInstance: AST.IsInstance =>
        visit(isInstance.target)

      case memberSel: AST.MemberSelection =>
        if (memberSel.target != null) visit(memberSel.target)

      case returnExpr: AST.ReturnExpression =>
        if (returnExpr.result != null) visit(returnExpr.result)

      case throwExpr: AST.ThrowExpression =>
        visit(throwExpr.target)

      case tryExpr: AST.TryExpression =>
        visit(tryExpr.tryBlock)
        tryExpr.recClauses.foreach { case (_, block) =>
          visit(block)
        }
        if (tryExpr.finBlock != null) visit(tryExpr.finBlock)

      case syncExpr: AST.SynchronizedExpression =>
        visit(syncExpr.condition)
        visit(syncExpr.block)

      case selectExpr: AST.SelectExpression =>
        visit(selectExpr.condition)
        selectExpr.cases.foreach { case (exprs, block) =>
          exprs.foreach(visit)
          visit(block)
        }
        if (selectExpr.elseBlock != null) visit(selectExpr.elseBlock)

      case exprBox: AST.ExpressionBox =>
        visit(exprBox.body)

      case stringInterp: AST.StringInterpolation =>
        stringInterp.expressions.foreach(visit)

      case _ =>
        ()
    }

    def visit(n: AST.Node): Unit = n match {
      case _: AST.ReturnExpression =>
        found = true
      case _: AST.ClosureExpression =>
        () // do not inspect nested closures
      case _ =>
        visitChildren(n)(visit)
    }

    visit(node)
    found
  }
}
