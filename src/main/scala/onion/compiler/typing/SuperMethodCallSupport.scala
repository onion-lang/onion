package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.typing.session.TypingBodyContext

private[compiler] final class SuperMethodCallSupport(
  bodyContext: TypingBodyContext,
  calls: MethodCallTyping
) {
  def typeSuperMethodCall(node: AST.SuperMethodCall, context: LocalContext, expected: Type = null): Option[Term] = {
    val parameters = calls.typedTerms(node.args.toArray, context)
    if (parameters == null) return None
    val contextClass = bodyContext.definition
    calls.tryFindMethod(node, contextClass.superClass, node.name, parameters) match {
      case Right(method) =>
        // The method may be declared further up (`Leaf[V] extends Mid[V]
        // extends Box[U]`), so substitute along the whole extends chain: with
        // the direct superclass's arguments alone, Box's `T` came back
        // unsubstituted (#2308).
        val classSubst = TypeSubstitution.hierarchySubstitution(contextClass.superClass, method.affiliation, selfReceiver = true)
        calls.buildResolvedCall(node, method, parameters, node.typeArgs, classSubst, expected)(
          expectedArgs => calls.prepareCallParams(node, node.args, method, parameters, expectedArgs),
          finalParams =>
            if (context.isClosure) superCallThroughAccessor(contextClass, method, finalParams)
            else new CallSuper(new This(contextClass), method, finalParams)
        )
      case Left(_) =>
        calls.reportMethodNotFound(node, contextClass, node.name, calls.types(parameters))
        None
    }
  }

  /**
   * `super.f(..)` inside a closure (#2636). The closure is a class of its own, so an `invokespecial`
   * of the superclass's method there fails verification. As Java does, the enclosing class gets a
   * synthetic method `access$super$f$..` whose body is the `super.f(..)`, and the closure calls it
   * on the enclosing instance. An omitted default stays in the accessor's body, so it is still
   * computed by the superclass's default method; the accessor takes only the arguments written.
   */
  private def superCallThroughAccessor(contextClass: ClassDefinition, method: Method, params: Array[Term]): Term = {
    val explicit = params.indices.filterNot(i => params(i).isInstanceOf[DefaultArgument])
    val mask = params.indices.map(i => if (explicit.contains(i)) '1' else '0').mkString
    val owner = method.affiliation.name.replace('.', '_')
    val signature = method.arguments.map(_.name).mkString(",").hashCode.toHexString
    val name = "access$super$" + method.name + "$" + owner + "$" + signature + "$" + mask
    val argTypes: Array[Type] = explicit.map(i => method.arguments(i)).toArray
    val existing = contextClass.methods(name)
    val accessor: MethodDefinition =
      if (existing.nonEmpty) existing(0).asInstanceOf[MethodDefinition]
      else {
        val acc = new MethodDefinition(null, 0, contextClass, name, argTypes, method.returnType, null, method.typeParameters)
        val ctx = new LocalContext
        ctx.setMethod(acc)
        val slots = explicit.zipWithIndex.map { case (i, k) => ctx.add("p" + k, method.arguments(i), false) }
        val superParams: Array[Term] = params.indices.map { i =>
          val k = explicit.indexOf(i)
          if (k < 0) params(i) else new RefLocal(0, slots(k), method.arguments(i)): Term
        }.toArray
        val call = new CallSuper(new This(contextClass), method, superParams)
        val body: Seq[ActionStatement] =
          if (method.returnType == BasicType.VOID) Seq(new ExpressionActionStatement(null, call), new Return(null, null))
          else Seq(new Return(null, call))
        acc.setBlock(new StatementBlock(null, body*))
        acc.setFrame(ctx.getContextFrame)
        contextClass.add(acc)
        acc
      }
    new Call(new OuterThis(contextClass), accessor, explicit.map(params(_)).toArray)
  }
}
