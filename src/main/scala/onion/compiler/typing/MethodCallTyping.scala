package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

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
    target.`type` match {
      case targetType: ObjectType =>
        return typeMethodCallOnObject(node, target, targetType, params, context, expected)
      case basicType: BasicType =>
        report(CANNOT_CALL_METHOD_ON_PRIMITIVE, node, basicType, node.name)
        return None
      case _ =>
        report(INVALID_METHOD_CALL_TARGET, node, target.`type`)
        return None
    }
  }

  private def typeMethodCallOnObject(node: AST.MethodCall, target: Term, targetType: ObjectType, params: Array[Term], context: LocalContext, expected: Type = null): Option[Term] = {
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
        if (expectedArgs.length != params.length) None
        else {
          var ok = true
          var i = 0
          while (i < expectedArgs.length && ok) {
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
    val call = new CallStatic(typeRef, method, params)
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
}
