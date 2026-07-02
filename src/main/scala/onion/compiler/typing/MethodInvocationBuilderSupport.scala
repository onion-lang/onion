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
    expected: Type
  )(
    prepareParams: Array[Type] => Option[Array[Term]],
    buildRawCall: Array[Term] => Term
  ): Option[Term] =
    for {
      resolved <- resolveInvocation(node, method, params, typeArgs, classSubst, expected)
      finalParams <- prepareParams(resolved.expectedArgs)
    } yield castCall(buildRawCall(injectDictionaries(node, method, finalParams, classSubst, resolved.methodSubst)), method, classSubst, resolved.methodSubst)

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
  private def injectDictionaries(
    node: AST.Node,
    method: Method,
    finalParams: Array[Term],
    classSubst: scala.collection.immutable.Map[String, Type],
    methodSubst: scala.collection.immutable.Map[String, Type]
  ): Array[Term] = {
    val dictSpecs = method.typeParameters.toSeq.flatMap(tp => tp.constraints.map(c => (tp.name, c)))
    if (dictSpecs.isEmpty) return finalParams
    val realCount = finalParams.length - dictSpecs.length
    if (realCount < 0) return finalParams
    val result = finalParams.clone()
    dictSpecs.zipWithIndex.foreach { case ((tpName, traitClass), i) =>
      val concrete = methodSubst.getOrElse(tpName, classSubst.getOrElse(tpName, null))
      if (concrete != null) dictionaryInstanceTerm(node, traitClass, concrete).foreach(t => result(realCount + i) = t)
    }
    result
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
