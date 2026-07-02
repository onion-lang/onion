package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

private[typing] final case class ResolvedMethodInvocation(
  methodSubst: scala.collection.immutable.Map[String, Type],
  expectedArgs: Array[Type]
)

private[compiler] final class MethodInvocationBuilderSupport(
  typing: Typing,
  calls: MethodCallTyping
) {
  def resolveInvocation(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type
  ): Option[ResolvedMethodInvocation] =
    calls.resolveMethodTypeArgs(node, method, params, typeArgs, classSubst, expected).map { methodSubst =>
      ResolvedMethodInvocation(methodSubst, TypeSubst.args(method, classSubst, methodSubst))
    }

  def buildResolvedCall(
    node: AST.Node,
    method: Method,
    params: Array[Term],
    typeArgs: List[AST.TypeNode],
    classSubst: scala.collection.immutable.Map[String, Type],
    expected: Type,
    context: LocalContext = null
  )(
    prepareParams: Array[Type] => Option[Array[Term]],
    buildRawCall: Array[Term] => Term
  ): Option[Term] =
    for {
      resolved <- resolveInvocation(node, method, params, typeArgs, classSubst, expected)
      finalParams <- prepareParams(resolved.expectedArgs)
      injected <- injectDictionaries(node, method, finalParams, classSubst, resolved.methodSubst, context)
    } yield castCall(buildRawCall(injected), method, classSubst, resolved.methodSubst)

  private def simpleName(n: String): String = { val i = n.lastIndexOf('.'); if (i >= 0) n.substring(i + 1) else n }
  private def boxedSimpleName(n: String): String = n match {
    case "int" => "Integer"; case "long" => "Long"; case "double" => "Double"
    case "float" => "Float"; case "boolean" => "Boolean"; case "byte" => "Byte"
    case "short" => "Short"; case "char" => "Character"; case other => simpleName(other)
  }

  /** The synthesized instance-class name for `Trait[Concrete]`, matching the name
    * Rewriting mangles from a source `instance Trait[Concrete]` declaration (a type
    * argument is always boxed here, so `Long` not `long`). Simple type arguments
    * only (v1). */
  private def instanceClassName(traitClass: ClassType, concrete: Type): String =
    simpleName(traitClass.name) + "$$" + boxedSimpleName(concrete.name)

  private def dictionaryInstanceTerm(node: AST.Node, traitClass: ClassType, concrete: Type): Option[Term] =
    typing.load(instanceClassName(traitClass, concrete)).flatMap { instanceClass =>
      val ctors = instanceClass.findConstructor(Array[Term]())
      if (ctors.isEmpty) None else Some(new NewObject(node.location, ctors(0), Array[Term]()))
    }

  /**
   * Type-class dictionary passing: a constrained generic like `sum[T: Numeric]`
   * carries one trailing dictionary parameter per (type parameter, trait). After
   * inference pins each `T`, fill those trailing slots with the resolved instance
   * (`new <mangled>()`); Rewriting already left them defaulted to null and made the
   * body call the dictionary. Non-constrained methods are untouched.
   */
  private def dictParamName(traitClass: ClassType, typeParamName: String): String =
    "dict$" + simpleName(traitClass.name) + "$" + typeParamName

  /** Resolve the dictionary term to pass for a `(typeParam, trait)`: a fresh
    * instance for a ground type, or a forward of the caller's own dictionary
    * parameter when the type is still abstract (a constrained caller's type
    * parameter). None (with a reported error) when neither is available. */
  private def resolveDictionary(node: AST.Node, traitClass: ClassType, concrete: Type, context: LocalContext): Option[Term] =
    concrete match {
      case tv: TypeVariableType =>
        // Forward the caller's dictionary parameter for this trait/type variable.
        val fwd = if (context == null) None else context.lookupOpt(dictParamName(traitClass, tv.name)).map(new RefLocal(_))
        if (fwd.isEmpty) typing.report(SemanticError.CLASS_NOT_FOUND, node, s"instance ${simpleName(traitClass.name)}[${tv.name}]")
        fwd
      case _ =>
        val term = dictionaryInstanceTerm(node, traitClass, concrete)
        if (term.isEmpty) typing.report(SemanticError.CLASS_NOT_FOUND, node, s"instance ${simpleName(traitClass.name)}[${simpleName(concrete.name)}]")
        term
    }

  /**
   * Type-class dictionary passing: a constrained generic like `sum[T: Numeric]`
   * carries one trailing dictionary parameter per (type parameter, trait). After
   * inference pins each `T`, fill those trailing slots with the resolved instance
   * (`new <mangled>()`) or a forward of the caller's own dictionary; a type with no
   * instance is a clean error (not a runtime null). Non-constrained methods are
   * untouched.
   */
  private def injectDictionaries(
    node: AST.Node,
    method: Method,
    finalParams: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type],
    context: LocalContext
  ): Option[Array[Term]] = {
    val dictSpecs = method.typeParameters.toSeq.flatMap(tp => tp.constraints.map(c => (tp.name, c)))
    if (dictSpecs.isEmpty) return Some(finalParams)
    val realCount = finalParams.length - dictSpecs.length
    if (realCount < 0) return Some(finalParams)
    val result = finalParams.clone()
    var ok = true
    dictSpecs.zipWithIndex.foreach { case ((tpName, traitClass), i) =>
      val concrete = methodSubst.getOrElse(tpName, classSubst.getOrElse(tpName, null))
      if (concrete == null) {
        typing.report(SemanticError.CLASS_NOT_FOUND, node, s"instance ${simpleName(traitClass.name)}[$tpName] (cannot infer $tpName)")
        ok = false
      } else resolveDictionary(node, traitClass, concrete, context) match {
        case Some(t) => result(realCount + i) = t
        case None => ok = false
      }
    }
    if (ok) Some(result) else None
  }

  def castCall(
    call: Term,
    method: Method,
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Term = {
    val castType = TypeSubst.result(method.returnType, classSubst, methodSubst)
    call match {
      case _: SafeCall | _: SafeFieldAccess =>
        // Safe navigation yields null when the target is null: keep the
        // nullable result type instead of casting back to the raw return
        // type (which unboxes primitives and NPEs on the null path)
        castType match {
          case _: NullableType | _: NullType => TypeSubst.withCast(call, castType)
          case bt: BasicType => call // SafeCall.type is already NullableType(bt)
          case other if other eq method.returnType => call
          case other => TypeSubst.withCast(call, NullableType.of(other))
        }
      case _ =>
        TypeSubst.withCast(call, castType)
    }
  }
}
