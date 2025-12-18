package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.generics.Erasure

import java.lang.reflect.Modifier
import scala.collection.mutable

private[compiler] object DuplicationChecks {
  private val emptyMethodSubst: scala.collection.immutable.Map[String, Type] =
    scala.collection.immutable.Map.empty

  private def erasedMethodDesc(method: Method): String =
    Erasure.methodDescriptor(method.returnType, method.arguments)

  private def erasedParamDescriptor(args: Array[Type]): String =
    args.map(Erasure.asmType).map(_.getDescriptor).mkString("(", "", ")")

  def checkOverrideContracts(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    import typing.*

    if clazz.isInterface then return
    val views = AppliedTypeViews.collectAppliedViewsFrom(clazz)
    if views.isEmpty then return

    val implByErasedParams: scala.collection.immutable.Map[(String, String), Method] =
      clazz.methods
        .filter(m => !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier))
        .map(m => ((m.name, erasedParamDescriptor(m.arguments)), m))
        .toMap

    for (view <- views.values) {
      val viewSubst: scala.collection.immutable.Map[String, Type] =
        view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap

      for (contract <- view.raw.methods) {
        if !Modifier.isStatic(contract.modifier) && !Modifier.isPrivate(contract.modifier) then
          val key = (contract.name, erasedParamDescriptor(contract.arguments))
          implByErasedParams.get(key).foreach { impl =>
            val specializedArgs =
              contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
            val specializedRet =
              TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)

            val implAst = lookupAST(impl.asInstanceOf[Node])
            val location = if implAst != null then implAst.location else fallback

            var i = 0
            while (i < specializedArgs.length && i < impl.arguments.length) {
              val arg = specializedArgs(i)
              val checkedArg = if (!impl.arguments(i).isBasicType && arg.isBasicType) boxedTypeArgument(arg) else arg
              if (!TypeRules.isSuperType(impl.arguments(i), checkedArg)) {
                report(SemanticError.INCOMPATIBLE_TYPE, location, specializedArgs(i), impl.arguments(i))
              }
              i += 1
            }

            if (!TypeRules.isAssignable(specializedRet, impl.returnType)) {
              report(SemanticError.INCOMPATIBLE_TYPE, location, specializedRet, impl.returnType)
            }
          }
      }
    }
  }

  def checkErasureSignatureCollisions(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    import typing.*

    val seen = mutable.HashMap[(String, String), Method]()
    for m <- clazz.methods do
      val key = (m.name, erasedMethodDesc(m))
      if seen.contains(key) then
        val ast = lookupAST(m.asInstanceOf[Node])
        val location = if ast != null then ast.location else fallback
        report(SemanticError.ERASURE_SIGNATURE_COLLISION, location, clazz, m.name, key._2)
      else
        seen(key) = m
  }
}

