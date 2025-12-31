package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.generics.Erasure

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
    val allViews = AppliedTypeViews.collectAppliedViewsFrom(clazz)
    // Exclude the target class itself to avoid checking methods against themselves
    val views = allViews - clazz
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

            // Check if trying to override a final method
            if Modifier.isFinal(contract.modifier) then
              val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
              report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, view.raw.name)

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

  def checkAbstractMethodImplementation(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    import typing.*

    // Skip if the class is abstract or an interface
    if Modifier.isAbstract(clazz.modifier) || clazz.isInterface then return

    val allViews = AppliedTypeViews.collectAppliedViewsFrom(clazz)
    // Exclude the target class itself to avoid checking methods against themselves
    val views = allViews - clazz
    if views.isEmpty then return

    // Collect all implemented methods by erased signature
    val implByErasedParams: scala.collection.immutable.Map[(String, String), Method] =
      clazz.methods
        .filter(m => !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier))
        .map(m => ((m.name, erasedParamDescriptor(m.arguments)), m))
        .toMap

    // Check all abstract methods from superclasses and interfaces
    for (view <- views.values) {
      val viewSubst: scala.collection.immutable.Map[String, Type] =
        view.raw.typeParameters.map(_.name).zip(view.typeArguments).toMap

      for (contract <- view.raw.methods) {
        if Modifier.isAbstract(contract.modifier) && !Modifier.isStatic(contract.modifier) && !Modifier.isPrivate(contract.modifier) then
          // Apply type substitution first, then erase
          // Example: Picker[String].pick(T) → pick(String) (NOT pick(Object))
          val specializedArgs =
            contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
          val key = (contract.name, erasedParamDescriptor(specializedArgs))

          if !implByErasedParams.contains(key) then
            val paramDescriptor = specializedArgs.map(_.name).mkString(", ")
            report(SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD, fallback, clazz.name, contract.name, paramDescriptor)
      }
    }
  }
}

