package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*
import onion.compiler.TypedAST.BinaryTerm.Constants.*

final class ExpressionFormTyping(private val typing: Typing, private val body: TypingBodyPass) {
  import typing.*

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
      report(CONSTRUCTOR_NOT_FOUND, node, sbType, Array[Type]())
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
      report(METHOD_NOT_FOUND, node, sbType, "toString", Array[Type]())
      return None
    }
    Some(new Call(result, toStringMethods(0), Array[Term]()))
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

  private def typed(node: AST.Expression, context: LocalContext, expected: Type = null): Option[Term] =
    body.typed(node, context, expected)

  private def typedTerms(nodes: Array[AST.Expression], context: LocalContext): Array[Term] =
    body.typedTerms(nodes, context)

  private def tryFindMethod(node: AST.Node, target: ObjectType, name: String, params: Array[Term]): Either[Boolean, Method] =
    body.tryFindMethod(node, target, name, params)

  private def types(terms: Array[Term]): Array[Type] =
    body.types(terms)
}
