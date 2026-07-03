package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

import scala.jdk.CollectionConverters.*

final class ClosureTyping(
  private val typing: Typing,
  private val bodyContext: TypingBodyContext,
  private val body: TypingBodyPass
) {

  def typeClosure(node: AST.ClosureExpression, context: LocalContext, expected: Type): Option[Term] = {
    val args = node.args
    val name = node.mname
    val inferredTarget: ClassType =
      expected match {
        case ct: ClassType if node.typeRef.isRelaxed && ct.isInterface => ct
        case _ => null
      }

    val rawTypeRef: ClassType =
      if (inferredTarget != null) inferredTarget
      else typing.mapFrom(node.typeRef) match {
        case Some(ct: ClassType) => ct
        case Some(other) =>
          bodyContext.report(INTERFACE_REQUIRED, node.typeRef, other)
          null
        case None => null
      }
    if (rawTypeRef == null) {
      None
    } else if (!rawTypeRef.isInterface) {
      bodyContext.report(INTERFACE_REQUIRED, node.typeRef, rawTypeRef)
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
            lastArg == bodyContext.rootClass || containsTypeVariable(lastArg)
          }
        case _ => false
      }

      val inferredReturnType =
        if ((inferredTarget == null || needsReturnTypeInference) && node.typeRef.isRelaxed) {
          val inferred =
            if (useExpressionBody) inferReturnTypeFromExpressionBody(node, context, argTypes)
            else inferReturnTypeFromReturns(node, context, argTypes)
          // A closure whose only produced value is `null` (NullType) or that
          // never returns normally (BottomType, e.g. a throw-only body) infers
          // to a bottom type, which is not a valid method return type: codegen's
          // default value for it is absent, yielding a value-less `areturn`
          // (VerifyError: operand stack underflow). Widen it to Object.
          inferred.map(t => if (t.isNullType || t.isBottomType) bodyContext.rootClass else t)
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
        method.arguments.map(tp => TypeCheckingHelpers.effectiveType(
          TypeSubstitution.substituteType(tp, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)))

      // ラムダの期待戻り値型を計算する際、残った型変数（外側メソッドの型パラメータ等）は
      // rootClassに置換する。これにより Function1<String, Future<U>> の U が Object になっても
      // ラムダ本体が Future<String> を返すとき、戻り値型チェックが通るようになる。
      def substitutedReturn(method: Method): Type =
        TypeSubstitution.substituteType(method.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = false)

      val implName = implementedMethodName(typeRef, name, argTypes.length)
      val candidates = typeRef.methods.filter(m => m.name == implName && m.arguments.length == argTypes.length)
      candidates.find(m => sameTypesOrBoxed(substitutedArgs(m), argTypes)) match {
        case None =>
          bodyContext.report(METHOD_NOT_FOUND, node, typeRef, implName, argTypes)
          None
        case Some(method) =>
          val expectedArgs = substitutedArgs(method)
          val expectedRet = substitutedReturn(method)

          // Use the lambda's declared argument types as the implementation method
          // signature so primitive parameters stay primitive. The generated bridge
          // method boxes/unboxes between the erased interface signature (Object
          // for type variables) and the primitive implementation signature.
          val typedMethod = new Method {
            def modifier: Int = method.modifier
            def affiliation: ClassType = method.affiliation
            def name: String = method.name
            override def arguments: Array[Type] = argTypes.clone()
            override def returnType: Type = expectedRet
            override def typeParameters: Array[TypedAST.TypeParameter] = Array()
          }

          body.openFrame(context) {
            body.openClosure(context) {
              if (!addClosureArguments(args, argTypes, context, node.body)) {
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
                    // Only insert a reference cast when both types are reference types.
                    // Primitive<->boxed mismatches are handled by the bridge method.
                    Option.when((desired ne erased) && !erased.isBasicType && !desired.isBasicType) {
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
        typing.mapFrom(arg.typeRef) match {
          case Some(mapped) => argTypes(i) = mapped
          case None => return None
        }
      }
      i += 1
    }

    if (!hasMissing) {
      Some(argTypes)
    } else if (inferredTarget == null) {
      args.zipWithIndex.foreach { case (arg, idx) =>
        if (arg.typeRef == null) bodyContext.report(LAMBDA_PARAM_TYPE_REQUIRED, arg, arg.name)
      }
      None
    } else {
      expectedMethodArgs(inferredTarget, implementedMethodName(inferredTarget, node.mname, args.length), args.length) match {
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

  /**
   * The method a closure implements on `target`: the onion.FunctionN-style
   * method named `name` when present, otherwise the interface's single
   * abstract method (SAM conversion for Java functional interfaces).
   * Abstract redeclarations of public java.lang.Object methods don't count,
   * per Java's functional interface rules (e.g. Comparator.equals).
   */
  private def implementedMethodName(target: ClassType, name: String, arity: Int): String = {
    if (target.methods.exists(m => m.name == name && m.arguments.length == arity)) name
    else {
      val abstracts = target.methods.filter { m =>
        Modifier.isAbstract(m.modifier) && !isPublicObjectMethod(m)
      }
      abstracts.map(_.name).distinct.toList match {
        case single :: Nil if abstracts.exists(_.arguments.length == arity) => single
        case _ => name
      }
    }
  }

  private def isPublicObjectMethod(m: Method): Boolean = m.name match {
    case "equals" => m.arguments.length == 1 && m.arguments(0).name == "java.lang.Object"
    case "hashCode" | "toString" => m.arguments.isEmpty
    case _ => false
  }

  /**
   * For SAM overload disambiguation (e.g. ExecutorService.submit): whether this
   * lambda is value/void compatible with `target` treated as a functional
   * interface. A value-producing body matches a non-void SAM (Callable), a void
   * body matches a void SAM (Runnable). Returns None when `target` is not a
   * single-abstract-method interface, the arities differ, the body's type can't
   * be inferred, or the body never returns normally (throw-only: compatible with
   * both) -- callers must not filter on None.
   */
  private[typing] def matchesSam(node: AST.ClosureExpression, context: LocalContext, target: Type): Option[Boolean] = {
    target match {
      case ct: ClassType =>
        val raw = ct match { case a: AppliedClassType => a.raw; case _ => ct }
        val abstracts = raw.methods.filter(m => Modifier.isAbstract(m.modifier) && !isPublicObjectMethod(m))
        abstracts.map(_.name).distinct.toList match {
          case single :: Nil =>
            abstracts.find(m => m.name == single && m.arguments.length == node.args.length) match {
              case Some(sam) =>
                val classSubst = TypeSubstitution.classSubstitution(ct)
                val samReturn = TypeSubstitution.substituteType(
                  sam.returnType, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
                val samArgs = sam.arguments.map(t => TypeSubstitution.substituteType(
                  t, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true))
                inferBodyReturnType(node, context, samArgs) match {
                  case Some(bodyReturn) if bodyReturn.isBottomType => None // throw-only: matches both
                  case Some(bodyReturn) =>
                    Some((bodyReturn == BasicType.VOID) == (samReturn == BasicType.VOID))
                  case None => None
                }
              case None => None
            }
          case _ => None
        }
      case _ => None
    }
  }

  private def inferBodyReturnType(node: AST.ClosureExpression, context: LocalContext, argTypes: Array[Type]): Option[Type] =
    if (containsReturn(node.body)) inferReturnTypeFromReturns(node, context, argTypes)
    else inferReturnTypeFromExpressionBody(node, context, argTypes)

  private def expectedMethodArgs(target: ClassType, name: String, arity: Int): Option[Array[Type]] = {
    val methodOpt = target.methods.find(m => m.name == name && m.arguments.length == arity)
    methodOpt.map { method =>
      val classSubst = TypeSubstitution.classSubstitution(target)
      method.arguments.map(tp => TypeCheckingHelpers.effectiveType(
        TypeSubstitution.substituteType(tp, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)))
    }
  }

  private def addClosureArguments(
    args: List[AST.Argument],
    argTypes: Array[Type],
    context: LocalContext,
    closureBody: AST.Node
  ): Boolean = {
    // Unassigned lambda parameters behave like vals so smart casts apply
    val assigned =
      if (closureBody == null) args.map(_.name).toSet
      else AssignedVariableScanner.scan(closureBody)
    var i = 0
    while (i < args.length) {
      val arg = args(i)
      if (context.lookupOnlyCurrentScope(arg.name) != null) {
        bodyContext.report(DUPLICATE_LOCAL_VARIABLE, arg, arg.name)
        return false
      }
      val argType = argTypes(i)
      if (argType == null) return false
      context.add(arg.name, argType, isMutable = assigned.contains(arg.name))
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
      // void cannot instantiate a type parameter; a side-effect-only body
      // becomes FunctionN[..., Object] and the block returns null
      val ret = inferredReturnType.filter(_ != BasicType.VOID).getOrElse(bodyContext.rootClass)
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
          if (addClosureArguments(node.args, argTypes, context, node.body)) {
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
          if (addClosureArguments(node.args, argTypes, context, node.body)) {
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
    TypeCheckingHelpers.leastUpperBound(node, left, right, bodyContext.rootClass,
      (n, l, r) => bodyContext.report(INCOMPATIBLE_TYPE, n, l, r))

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
      } else if (last.`type` == BasicType.VOID && !returnType.isBasicType) {
        // Side-effect-only body against a reference-typed SAM return
        // (e.g. Future::async(() -> IO::println(...))): run it, return null
        statements += termToStatement(last)
        new Return(new NullValue(node.location))
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

  private def containsReturn(node: AST.Node): Boolean =
    ReturnNodeDetector.containsReturn(node)

  /**
   * Check whether the lambda's actual argument types match the substituted
   * interface method argument types, allowing primitive types to match their
   * boxed counterparts. This is needed because Onion boxes primitive type
   * arguments at the generic-interface level (e.g. `Comparator[Int]` is stored
   * as `Comparator[Integer]`) while users may still write primitive-typed
   * lambda parameters.
   */
  private def sameTypesOrBoxed(expected: Array[Type], actual: Array[Type]): Boolean = {
    if (expected.length != actual.length) return false
    expected.indices.forall(i =>
      sameOrBoxed(
        TypeCheckingHelpers.effectiveType(expected(i)),
        TypeCheckingHelpers.effectiveType(actual(i))
      )
    )
  }

  private def sameOrBoxed(expected: Type, actual: Type): Boolean =
    TypeCheckingHelpers.sameOrBoxed(bodyContext.table, expected, actual)
}
