package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.toolbox.Boxing

import java.util.{TreeSet => JTreeSet}

import scala.jdk.CollectionConverters.*

final class MethodCallTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

  private sealed trait StaticImportLookup
  private case class StaticImportFound(term: Term) extends StaticImportLookup
  private case object StaticImportNotFound extends StaticImportLookup
  private case object StaticImportError extends StaticImportLookup

  private sealed trait StaticImportResolution
  private case class StaticImportResolved(method: Method, term: Term) extends StaticImportResolution
  private case class StaticImportAmbiguous(first: Method, second: Method) extends StaticImportResolution
  private case object StaticImportNoMatch extends StaticImportResolution
  private case class StaticApplicable(method: Method, expectedArgs: Array[Type], methodSubst: scala.collection.immutable.Map[String, Type])

  def typeMemberSelection(node: AST.MemberSelection, context: LocalContext): Option[Term] = {
    val contextClass = definition_
    var target = typed(node.target, context).getOrElse(null)
    if (target == null) return None
    if (target.`type`.isNullType) {
      report(INCOMPATIBLE_TYPE, node.target, rootClass, target.`type`)
      return None
    }

    // プリミティブ型の場合はボクシング
    if (target.`type`.isBasicType) {
      val basicType = target.`type`.asInstanceOf[BasicType]
      if (basicType == BasicType.VOID) {
        report(INCOMPATIBLE_TYPE, node.target, rootClass, basicType)
        return None
      }
      target = Boxing.boxing(table_, target)
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
    var target = typed(node.target, context).getOrElse(null)
    if (target == null) return None
    val params = typedTerms(node.args.toArray, context)
    if (params == null) return None
    target.`type` match {
      case targetType: ObjectType =>
        return typeMethodCallOnObject(node, target, targetType, params, context, expected)
      case basicType: BasicType =>
        // オートボクシング: プリミティブ型をラッパークラスに変換
        if (basicType == BasicType.VOID) {
          report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, node.name)
          return None
        }
        target = Boxing.boxing(table_, target)
        return typeMethodCallOnObject(node, target, target.`type`.asInstanceOf[ObjectType], params, context, expected)
      case _ =>
        report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
        return None
    }
  }

  private def typeMethodCallOnObject(node: AST.MethodCall, target: Term, targetType: ObjectType, params: Array[Term], context: LocalContext, expected: Type = null): Option[Term] = {
    val name = node.name

    // 名前付き引数がある場合は特別な処理
    if (hasNamedArguments(node.args)) {
      return typeMethodCallWithNamedArgs(node, target, targetType, context, expected)
    }

    val methods = MethodResolution.findMethods(targetType, name, params, table_)
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

      // デフォルト引数で足りない分を補完
      val finalParams = fillDefaultArguments(params, method)

      val call = new Call(target, method, finalParams)
      val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
      Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
    }
  }

  private def typeMethodCallWithNamedArgs(node: AST.MethodCall, target: Term, targetType: ObjectType, context: LocalContext, expected: Type): Option[Term] = {
    val name = node.name

    // 名前付き引数がある場合は、全てのメソッドから名前でフィルタリング
    val candidates = new JTreeSet[Method](new MethodComparator)

    def collectMethods(tp: ObjectType): Unit = {
      if (tp == null) return
      tp.methods(name).foreach { m =>
        if ((m.modifier & AST.M_STATIC) == 0) candidates.add(m)
      }
      collectMethods(tp.superClass)
      tp.interfaces.foreach(collectMethods)
    }

    collectMethods(targetType)
    if (candidates.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, name, Array[Type]())
      return None
    }

    // 名前付き引数の名前を取得
    val namedArgNames = node.args.collect { case na: AST.NamedArgument => na.name }.toSet
    val positionalCount = node.args.takeWhile(!_.isInstanceOf[AST.NamedArgument]).size

    // パラメータ名が一致するメソッドをフィルタ
    val applicable = candidates.asScala.filter { method =>
      val paramNames = method.argumentsWithDefaults.map(_.name).toSet
      namedArgNames.subsetOf(paramNames) && positionalCount <= method.arguments.length
    }.toList

    if (applicable.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, name, Array[Type]())
      None
    } else if (applicable.length > 1) {
      report(
        AMBIGUOUS_METHOD,
        node,
        Array[AnyRef](applicable(0).affiliation, name, applicable(0).arguments),
        Array[AnyRef](applicable(1).affiliation, name, applicable(1).arguments)
      )
      None
    } else {
      val method = applicable.head
      val classSubst = TypeSubstitution.classSubstitution(target.`type`)

      // 名前付き引数を含めて処理
      processNamedArguments(node, node.args, method, context) match {
        case Some(params) =>
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
          var hasError = false
          while (i < params.length && !hasError) {
            val processed = processAssignable(node, expectedArgs(i), params(i))
            if (processed == null) {
              hasError = true
            } else {
              params(i) = processed
            }
            i += 1
          }
          if (hasError) return None

          val call = new Call(target, method, params)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        case None =>
          None
      }
    }
  }

  def typeUnqualifiedMethodCall(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    // 名前付き引数がある場合は特別な処理
    if (hasNamedArguments(node.args)) {
      return typeUnqualifiedMethodCallWithNamedArgs(node, context, expected)
    }

    var params = typedTerms(node.args.toArray, context)
    if (params == null) return None
    val targetType = definition_
    val methods = MethodResolution.findMethods(targetType, node.name, params, table_)
    if (methods.length == 0) {
      resolveStaticImportMethodCall(node, params, expected) match {
        case StaticImportFound(term) =>
          Some(term)
        case StaticImportError =>
          None
        case StaticImportNotFound =>
          report(METHOD_NOT_FOUND, node, targetType, node.name, types(params))
          None
      }
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

      // デフォルト引数で足りない分を補完
      val finalParams = fillDefaultArguments(params, method)

      if ((methods(0).modifier & AST.M_STATIC) != 0) {
        val call = new CallStatic(targetType, method, finalParams)
        val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
        Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      } else {
        if (context.isClosure) {
          val call = new Call(new OuterThis(targetType), method, finalParams)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        } else {
          val call = new Call(new This(targetType), method, finalParams)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        }
      }
    }
  }

  private def typeUnqualifiedMethodCallWithNamedArgs(node: AST.UnqualifiedMethodCall, context: LocalContext, expected: Type): Option[Term] = {
    val targetType = definition_

    // 名前付き引数がある場合は、全てのメソッドから名前でフィルタリング
    val candidates = new JTreeSet[Method](new MethodComparator)

    def collectMethods(tp: ObjectType): Unit = {
      if (tp == null) return
      tp.methods(node.name).foreach(candidates.add)
      collectMethods(tp.superClass)
      tp.interfaces.foreach(collectMethods)
    }

    collectMethods(targetType)
    if (candidates.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, node.name, Array[Type]())
      return None
    }

    // 名前付き引数の名前を取得
    val namedArgNames = node.args.collect { case na: AST.NamedArgument => na.name }.toSet
    val positionalCount = node.args.takeWhile(!_.isInstanceOf[AST.NamedArgument]).size

    // パラメータ名が一致するメソッドをフィルタ
    val applicable = candidates.asScala.filter { method =>
      val paramNames = method.argumentsWithDefaults.map(_.name).toSet
      namedArgNames.subsetOf(paramNames) && positionalCount <= method.arguments.length
    }.toList

    if (applicable.isEmpty) {
      report(METHOD_NOT_FOUND, node, targetType, node.name, Array[Type]())
      None
    } else if (applicable.length > 1) {
      report(
        AMBIGUOUS_METHOD,
        node,
        Array[AnyRef](applicable(0).affiliation, node.name, applicable(0).arguments),
        Array[AnyRef](applicable(1).affiliation, node.name, applicable(1).arguments)
      )
      None
    } else {
      val method = applicable.head
      val classSubst: scala.collection.immutable.Map[String, Type] = scala.collection.immutable.Map.empty

      // 名前付き引数を含めて処理
      processNamedArguments(node, node.args, method, context) match {
        case Some(params) =>
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
          var hasError = false
          while (i < params.length && !hasError) {
            val processed = processAssignable(node, expectedArgs(i), params(i))
            if (processed == null) {
              hasError = true
            } else {
              params(i) = processed
            }
            i += 1
          }
          if (hasError) return None

          if ((method.modifier & AST.M_STATIC) != 0) {
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
        case None =>
          None
      }
    }
  }

  private def resolveStaticImportMethodCall(
    node: AST.UnqualifiedMethodCall,
    params: Array[Term],
    expected: Type
  ): StaticImportLookup = {
    val mappedTypeArgs =
      if (node.typeArgs.nonEmpty) {
        mapTypeArgs(node.typeArgs) match {
          case Some(mapped) => Some(mapped)
          case None => return StaticImportError
        }
      } else {
        None
      }

    val resolved = scala.collection.mutable.Buffer[StaticImportResolved]()
    var ambiguous: Option[StaticImportAmbiguous] = None
    staticImportedList_.getItems.foreach { item =>
      val typeRef = load(item.getName)
      if (typeRef != null) {
        resolveStaticImportOnType(node, typeRef, params, expected, mappedTypeArgs) match {
          case found: StaticImportResolved =>
            resolved += found
          case amb: StaticImportAmbiguous =>
            if (ambiguous.isEmpty) ambiguous = Some(amb)
          case StaticImportNoMatch =>
        }
      }
    }

    if (resolved.length == 1) {
      StaticImportFound(resolved.head.term)
    } else if (resolved.length > 1) {
      reportAmbiguousMethod(node, resolved(0).method, resolved(1).method)
      StaticImportError
    } else {
      ambiguous match {
        case Some(amb) =>
          reportAmbiguousMethod(node, amb.first, amb.second)
          StaticImportError
        case None =>
          StaticImportNotFound
      }
    }
  }

  private def resolveStaticImportOnType(
    node: AST.UnqualifiedMethodCall,
    typeRef: ClassType,
    params: Array[Term],
    expected: Type,
    mappedTypeArgs: Option[Array[Type]]
  ): StaticImportResolution = {
    val name = node.name
    val candidates = new JTreeSet[Method](new MethodComparator)

    def collectStatics(tp: ObjectType): Unit = {
      if (tp == null) return
      tp.methods(name).foreach { m =>
        if ((m.modifier & AST.M_STATIC) != 0) candidates.add(m)
      }
      collectStatics(tp.superClass)
      tp.interfaces.foreach(collectStatics)
    }

    collectStatics(typeRef)
    if (candidates.isEmpty) return StaticImportNoMatch

    val applicable = candidates.asScala.flatMap { method =>
      val classSubst = TypeSubstitution.classSubstitution(typeRef)
      val methodSubstOpt = mappedTypeArgs match {
        case Some(mapped) =>
          GenericMethodTypeArguments.explicitFromMappedArgs(
            typing,
            node,
            method,
            mapped,
            classSubst,
            reportErrors = false
          )
        case None =>
          Some(GenericMethodTypeArguments.infer(typing, node, method, params, classSubst, expected))
      }
      methodSubstOpt.flatMap { methodSubst =>
        val expectedArgs = method.arguments.map(tp => TypeSubstitution.substituteType(tp, classSubst, methodSubst, defaultToBound = true))
        // デフォルト引数を考慮: minArguments <= params.length <= expectedArgs.length
        if (params.length < method.minArguments || params.length > expectedArgs.length) None
        else {
          var ok = true
          var i = 0
          while (i < params.length && ok) {
            ok = TypeRules.isAssignable(expectedArgs(i), params(i).`type`)
            i += 1
          }
          if (ok) Some(StaticApplicable(method, expectedArgs, methodSubst)) else None
        }
      }
    }.toList

    if (applicable.isEmpty) return StaticImportNoMatch

    selectStaticApplicable(applicable) match {
      case Left(amb) =>
        StaticImportAmbiguous(amb.first, amb.second)
      case Right(chosen) =>
        val classSubst = TypeSubstitution.classSubstitution(typeRef)
        val adjusted = params.clone()
        var i = 0
        while (i < adjusted.length) {
          adjusted(i) = processAssignable(node.args(i), chosen.expectedArgs(i), adjusted(i))
          if (adjusted(i) == null) return StaticImportNoMatch
          i += 1
        }
        StaticImportResolved(chosen.method, buildStaticCall(typeRef, chosen.method, adjusted, classSubst, chosen.methodSubst))
    }
  }

  private def selectStaticApplicable(
    applicable: List[StaticApplicable]
  ): Either[StaticImportAmbiguous, StaticApplicable] = {
    if (applicable.length == 1) return Right(applicable.head)

    def isAllSuperType(a: Array[Type], b: Array[Type]): Boolean =
      var i = 0
      while i < a.length do
        if !TypeRules.isSuperType(a(i), b(i)) then return false
        i += 1
      true

    val sorter: java.util.Comparator[StaticApplicable] = new java.util.Comparator[StaticApplicable] {
      def compare(a1: StaticApplicable, a2: StaticApplicable): Int =
        if isAllSuperType(a2.expectedArgs, a1.expectedArgs) then -1
        else if isAllSuperType(a1.expectedArgs, a2.expectedArgs) then 1
        else 0
    }

    val selected = new java.util.ArrayList[StaticApplicable]()
    selected.addAll(applicable.asJava)
    java.util.Collections.sort(selected, sorter)
    if (selected.size < 2) {
      Right(selected.get(0))
    } else {
      val a1 = selected.get(0)
      val a2 = selected.get(1)
      if (sorter.compare(a1, a2) >= 0) Left(StaticImportAmbiguous(a1.method, a2.method))
      else Right(a1)
    }
  }

  private def mapTypeArgs(typeArgs: List[AST.TypeNode]): Option[Array[Type]] = {
    val mapped = new Array[Type](typeArgs.length)
    var i = 0
    while (i < typeArgs.length) {
      val mappedType = mapFrom(typeArgs(i))
      if (mappedType == null) return None
      if (mappedType eq BasicType.VOID) {
        report(TYPE_ARGUMENT_MUST_BE_REFERENCE, typeArgs(i), mappedType.name)
        return None
      }
      mapped(i) = mappedType
      i += 1
    }
    Some(mapped)
  }

  private def buildStaticCall(
    typeRef: ClassType,
    method: Method,
    params: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Term = {
    // デフォルト引数で足りない分を補完
    val finalParams = fillDefaultArguments(params, method)
    val call = new CallStatic(typeRef, method, finalParams)
    val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
    if (castType eq method.returnType) call else new AsInstanceOf(call, castType)
  }

  private def reportAmbiguousMethod(node: AST.Node, first: Method, second: Method): Unit = {
    report(
      AMBIGUOUS_METHOD,
      node,
      Array[AnyRef](first.affiliation, first.name, first.arguments),
      Array[AnyRef](second.affiliation, second.name, second.arguments)
    )
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
    if (typeRef == null) return None

    // 名前付き引数がある場合は特別な処理
    if (hasNamedArguments(node.args)) {
      return typeStaticMethodCallWithNamedArgs(node, typeRef, context, expected)
    }

    val parameters = typedTerms(node.args.toArray, context)
    if (parameters == null) {
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

          // デフォルト引数で足りない分を補完
          val finalParams = fillDefaultArguments(parameters, method)
          val call = new CallStatic(typeRef, method, finalParams)
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
          // デフォルト引数を考慮: minArguments <= parameters.length <= expectedArgs.length
          if (parameters.length < method.minArguments || parameters.length > expectedArgs.length) None
          else {
            var ok = true
            var i = 0
            while (i < parameters.length && ok) {
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
          // デフォルト引数で足りない分を補完
          val finalParams = fillDefaultArguments(parameters, selected.method)
          val call = new CallStatic(typeRef, selected.method, finalParams)
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
            // デフォルト引数で足りない分を補完
            val finalParams = fillDefaultArguments(parameters, only.method)
            val call = new CallStatic(typeRef, only.method, finalParams)
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
              // デフォルト引数で足りない分を補完
              val finalParams = fillDefaultArguments(parameters, a1.method)
              val call = new CallStatic(typeRef, a1.method, finalParams)
              val castType = TypeSubstitution.substituteType(a1.method.returnType, classSubst, a1.methodSubst, defaultToBound = true)
              Some(if (castType eq a1.method.returnType) call else new AsInstanceOf(call, castType))
            }
          }
        }
      }
    }
  }

  private def typeStaticMethodCallWithNamedArgs(node: AST.StaticMethodCall, typeRef: ClassType, context: LocalContext, expected: Type): Option[Term] = {
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
      report(METHOD_NOT_FOUND, node, typeRef, node.name, Array[Type]())
      return None
    }

    // 名前付き引数の名前を取得
    val namedArgNames = node.args.collect { case na: AST.NamedArgument => na.name }.toSet
    val positionalCount = node.args.takeWhile(!_.isInstanceOf[AST.NamedArgument]).size

    // パラメータ名が一致するメソッドをフィルタ
    val applicable = candidates.asScala.filter { method =>
      val paramNames = method.argumentsWithDefaults.map(_.name).toSet
      namedArgNames.subsetOf(paramNames) && positionalCount <= method.arguments.length
    }.toList

    if (applicable.isEmpty) {
      report(METHOD_NOT_FOUND, node, typeRef, node.name, Array[Type]())
      None
    } else if (applicable.length > 1) {
      report(AMBIGUOUS_METHOD, node, node.name, typeNames(applicable(0).arguments), typeNames(applicable(1).arguments))
      None
    } else {
      val method = applicable.head
      val classSubst = TypeSubstitution.classSubstitution(typeRef)

      // 名前付き引数を含めて処理
      processNamedArguments(node, node.args, method, context) match {
        case Some(params) =>
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
          var hasError = false
          while (i < params.length && !hasError) {
            val processed = processAssignable(node, expectedArgs(i), params(i))
            if (processed == null) {
              hasError = true
            } else {
              params(i) = processed
            }
            i += 1
          }
          if (hasError) return None

          val call = new CallStatic(typeRef, method, params)
          val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
          Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
        case None =>
          None
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
        // デフォルト引数で足りない分を補完
        val finalParams = fillDefaultArguments(parameters, method)
        val call = new CallSuper(new This(contextClass), method, finalParams)
        val castType = TypeSubstitution.substituteType(method.returnType, classSubst, methodSubst, defaultToBound = true)
        Some(if (castType eq method.returnType) call else new AsInstanceOf(call, castType))
      case Left(_) =>
        report(METHOD_NOT_FOUND, node, contextClass, node.name, types(parameters))
        None
    }
  }

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] =
    body.typedTerms(nodes, context)

  private def processAssignable(node: AST.Node, expected: Type, term: Term): Term =
    body.processAssignable(node, expected, term)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)

  private def typeNames(types: Array[Type]): Array[String] =
    body.typeNames(types)

  private def getter(name: String): String =
    "get" + Character.toUpperCase(name.charAt(0)) + name.substring(1)

  private def getterBoolean(name: String): String =
    "is" + Character.toUpperCase(name.charAt(0)) + name.substring(1)

  /**
   * 名前付き引数を含む引数リストを並べ替えて型付けする
   * @return 成功時は Some(並べ替えられた引数配列), エラー時は None
   */
  private def processNamedArguments(
    node: AST.Node,
    args: List[AST.Expression],
    method: Method,
    context: LocalContext
  ): Option[Array[Term]] = {
    val argsWithDefaults = method.argumentsWithDefaults
    val paramNames = argsWithDefaults.map(_.name)
    val result = new Array[Term](argsWithDefaults.length)
    val filled = new Array[Boolean](argsWithDefaults.length)

    var positionalIndex = 0
    var sawNamed = false
    var hasError = false

    // 位置引数と名前付き引数を処理
    args.foreach { arg =>
      arg match {
        case named: AST.NamedArgument =>
          sawNamed = true
          // パラメータ名を検索
          val paramIndex = paramNames.indexOf(named.name)
          if (paramIndex < 0) {
            report(UNKNOWN_PARAMETER_NAME, named, named.name)
            hasError = true
          } else if (filled(paramIndex)) {
            report(DUPLICATE_ARGUMENT, named, named.name)
            hasError = true
          } else {
            // 値を型付け
            typed(named.value, context) match {
              case Some(term) =>
                result(paramIndex) = term
                filled(paramIndex) = true
              case None =>
                hasError = true
            }
          }

        case expr =>
          // 位置引数
          if (sawNamed) {
            report(POSITIONAL_AFTER_NAMED, expr)
            hasError = true
          } else if (positionalIndex >= argsWithDefaults.length) {
            // 引数が多すぎる - これは別のエラーで処理される
            typed(expr, context) // 型付けだけして結果は無視
            positionalIndex += 1
          } else {
            typed(expr, context) match {
              case Some(term) =>
                result(positionalIndex) = term
                filled(positionalIndex) = true
                positionalIndex += 1
              case None =>
                hasError = true
                positionalIndex += 1
            }
          }
      }
    }

    if (hasError) return None

    // 足りない引数をデフォルト値で補完
    var i = 0
    while (i < argsWithDefaults.length) {
      if (!filled(i)) {
        argsWithDefaults(i).defaultValue match {
          case Some(defaultTerm) =>
            result(i) = defaultTerm
            filled(i) = true
          case None =>
            // 必須引数が指定されていない
            report(METHOD_NOT_FOUND, node, method.affiliation, method.name, argsWithDefaults.map(_.argType))
            return None
        }
      }
      i += 1
    }

    Some(result)
  }

  /**
   * 引数リストに名前付き引数が含まれているか確認
   */
  private def hasNamedArguments(args: List[AST.Expression]): Boolean =
    args.exists(_.isInstanceOf[AST.NamedArgument])

  /**
   * デフォルト引数で足りない分を補完する
   */
  private def fillDefaultArguments(params: Array[Term], method: Method): Array[Term] = {
    val argsWithDefaults = method.argumentsWithDefaults
    if (params.length >= argsWithDefaults.length) {
      params
    } else {
      val result = new Array[Term](argsWithDefaults.length)
      System.arraycopy(params, 0, result, 0, params.length)
      var i = params.length
      while (i < argsWithDefaults.length) {
        argsWithDefaults(i).defaultValue match {
          case Some(term) => result(i) = term
          case None => throw new IllegalStateException(s"Missing default value for argument ${argsWithDefaults(i).name}")
        }
        i += 1
      }
      result
    }
  }
}
