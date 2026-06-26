package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*
import onion.compiler.generics.Erasure
import onion.compiler.toolbox.Boxing

import scala.collection.mutable

private[compiler] object DuplicationChecks {
  private val emptyMethodSubst: scala.collection.immutable.Map[String, Type] =
    scala.collection.immutable.Map.empty

  private def erasedMethodDesc(method: Method): String =
    Erasure.methodDescriptor(method.returnType, method.arguments)

  private def erasedParamDescriptor(args: Array[Type]): String =
    args.map(Erasure.asmType).map(_.getDescriptor).mkString("(", "", ")")

  private def allErasedParamDescriptors(args: Array[Type], typing: Typing): Set[String] = {
    val base = erasedParamDescriptor(args)
    val variants = Set.newBuilder[String]
    variants += base
    // For each argument that is a boxed primitive, also generate a descriptor
    // where that argument uses the primitive form, and vice versa. This lets
    // a user implement `compare(a: Int, b: Int)` for `Comparator[Int]`.
    if args.nonEmpty then
      val perArg = args.map { arg =>
        val baseDesc = Erasure.asmType(arg).getDescriptor
        arg match
          case bt: BasicType if bt != BasicType.VOID =>
            Set(baseDesc, Erasure.asmType(typing.boxedTypeArgument(bt)).getDescriptor)
          case ct: ClassType =>
            val base = Set(baseDesc)
            Boxing.unboxedType(typing.table_, ct) match
              case Some(bt) => base + Erasure.asmType(bt).getDescriptor
              case None => base
          case _ =>
            Set(baseDesc)
      }
      // Combine descriptors position-wise.
      def combine(index: Int, current: String): Unit =
        if index == args.length then
          variants += current + ")"
        else
          perArg(index).foreach(desc => combine(index + 1, current + desc))
      combine(0, "(")
    variants.result()
  }

  private def primitiveAwareSuperType(implArg: Type, contractArg: Type, typing: Typing): Boolean = {
    if TypeRules.isSuperType(implArg, contractArg) then return true
    (implArg, contractArg) match
      case (bt: BasicType, ct: ClassType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case (ct: ClassType, bt: BasicType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case _ => false
  }

  private def primitiveAwareAssignable(contractType: Type, implType: Type, typing: Typing): Boolean = {
    if TypeRules.isAssignable(contractType, implType) then return true
    (contractType, implType) match
      case (bt: BasicType, ct: ClassType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case (ct: ClassType, bt: BasicType) if bt != BasicType.VOID &&
          typing.boxedTypeArgument(bt).name == ct.name =>
        true
      case _ => false
  }

  def checkOverrideContracts(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
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
          val specializedArgs =
            contract.arguments.map(tp => TypeSubstitution.substituteType(tp, viewSubst, emptyMethodSubst, defaultToBound = true))
          // An implementation may declare the specialized parameter types
          // (id(x: String) for Id[String]) or the erased ones (id(x: Object)):
          // look the contract up under both keys.
          val specializedKey = (contract.name, erasedParamDescriptor(specializedArgs))
          val erasedKey = (contract.name, erasedParamDescriptor(contract.arguments))
          implByErasedParams.get(specializedKey).orElse(implByErasedParams.get(erasedKey)).foreach { impl =>
            val specializedRet =
              TypeSubstitution.substituteType(contract.returnType, viewSubst, emptyMethodSubst, defaultToBound = true)

            val location = typing.lookupAST(impl.asInstanceOf[Node]).map(_.location).getOrElse(fallback)

            // Check if trying to override a final method
            if Modifier.isFinal(contract.modifier) then
              val paramDescriptor = impl.arguments.map(_.name).mkString(", ")
              typing.report(SemanticError.FINAL_METHOD_OVERRIDE, location, impl.name, paramDescriptor, view.raw.name)

            specializedArgs.zip(impl.arguments).foreach { case (arg, implArg) =>
              if (!primitiveAwareSuperType(implArg, arg, typing)) {
                typing.report(SemanticError.INCOMPATIBLE_TYPE, location, arg, implArg)
              }
            }

            if (!primitiveAwareAssignable(specializedRet, impl.returnType, typing)) {
              typing.report(SemanticError.INCOMPATIBLE_TYPE, location, specializedRet, impl.returnType)
            }
          }
      }
    }
  }

  def checkErasureSignatureCollisions(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    val seen = mutable.HashMap[(String, String), Method]()
    for m <- clazz.methods do
      val key = (m.name, erasedMethodDesc(m))
      if seen.contains(key) then
        val location = typing.lookupAST(m.asInstanceOf[Node]).map(_.location).getOrElse(fallback)
        typing.report(SemanticError.ERASURE_SIGNATURE_COLLISION, location, clazz, m.name, key._2)
      else
        seen(key) = m
  }

  def checkAbstractMethodImplementation(typing: Typing, clazz: ClassDefinition, fallback: Location): Unit = {
    // Skip if the class is abstract or an interface
    if Modifier.isAbstract(clazz.modifier) || clazz.isInterface then return

    val allViews = AppliedTypeViews.collectAppliedViewsFrom(clazz)
    // Exclude the target class itself to avoid checking methods against themselves
    val views = allViews - clazz
    if views.isEmpty then return

    // Collect all implemented methods from this class AND all ancestor classes
    // Parent classes may already provide concrete implementations for abstract methods
    val implByErasedParams = mutable.HashMap[(String, String), Method]()

    // Add this class's own methods
    for m <- clazz.methods do
      if !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier) then
        implByErasedParams((m.name, erasedParamDescriptor(m.arguments))) = m

    // Also add concrete methods from parent classes (not just interfaces)
    for view <- views.values do
      for m <- view.raw.methods do
        if !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier) && !Modifier.isAbstract(m.modifier) then
          val key = (m.name, erasedParamDescriptor(m.arguments))
          if !implByErasedParams.contains(key) then
            implByErasedParams(key) = m

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
          val possibleKeys = allErasedParamDescriptors(specializedArgs, typing).map(desc => (contract.name, desc))

          if !possibleKeys.exists(implByErasedParams.contains) then
            val paramDescriptor = specializedArgs.map(_.name).mkString(", ")
            typing.report(SemanticError.UNIMPLEMENTED_ABSTRACT_METHOD, fallback, clazz.name, contract.name, paramDescriptor)
      }
    }
  }
}
