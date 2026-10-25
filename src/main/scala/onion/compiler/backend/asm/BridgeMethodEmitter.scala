package onion.compiler.backend.asm

import onion.compiler.{Modifier, TypedAST}
import onion.compiler.TypedAST.*
import org.objectweb.asm.{ClassWriter, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{Method => AsmMethod}

import scala.collection.mutable

final class BridgeMethodEmitter(private val codegen: AsmCodeGeneration) {
  import codegen.*

  def emitBridges(cw: ClassWriter, classDef: ClassDefinition): Unit =
    val sources = collectSupertypes(classDef)
    if sources.isEmpty then return

    def methodDesc(ret: TypedAST.Type, args: Array[TypedAST.Type]): String =
      AsmType.getMethodDescriptor(asmType(ret), args.map(asmType)*)

    // Seeded with the class's real methods and grown as bridges are emitted, so
    // a method declared at several levels of the generic hierarchy (e.g. addLast
    // on both List and SequencedCollection) yields a single bridge instead of a
    // duplicate (which would be a ClassFormatError).
    val ownMethods = classDef.methods // one copy: `methods` builds a fresh Seq per call
    val emitted = scala.collection.mutable.HashSet.from(ownMethods.map { m =>
      (m.name, methodDesc(m.returnType, m.arguments))
    })
    // Indexed by name: the search below ran over every method of the class for every
    // method of every generic supertype, with per-argument compatibility checks.
    val ownByName = ownMethods.groupBy(_.name)

    for (sourceRaw, subst) <- sources do
      def substituteClassParams(tp: TypedAST.Type): TypedAST.Type = substituteTypeVars(tp, subst)

      for rawMethod <- sourceRaw.methods do
        if Modifier.isStatic(rawMethod.modifier) || Modifier.isPrivate(rawMethod.modifier) then
          ()
        else
          val sameName = ownByName.getOrElse(rawMethod.name, Nil)
          val specializedArgs = if sameName.isEmpty then null else rawMethod.arguments.map(substituteClassParams)
          val implOpt = if sameName.isEmpty then None else sameName.find { m =>
            m.arguments.length == specializedArgs.length && (
              if subst.isEmpty then
                // A non-generic supertype: only a covariant return override, as
                // in Java. `foo(x: Int)` beside an inherited `foo(Integer)` is
                // an overload, so arguments must match exactly.
                m.arguments.indices.forall(i => m.arguments(i).name == specializedArgs(i).name) &&
                isCovariantReturn(rawMethod.returnType, m.returnType)
              else
                m.arguments.indices.forall(i => isBridgeCompatible(m.arguments(i), specializedArgs(i)))
            )
          }

          implOpt.foreach { impl =>
            if Modifier.isAbstract(impl.modifier) || Modifier.isStatic(impl.modifier) then
              ()
            else
              val bridgeDesc = methodDesc(rawMethod.returnType, rawMethod.arguments)
              val implDesc = methodDesc(impl.returnType, impl.arguments)
              if bridgeDesc != implDesc && !emitted.contains((rawMethod.name, bridgeDesc)) then
                emitted.add((rawMethod.name, bridgeDesc))
                val access = toAsmModifier(impl.modifier) | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC
                val bridgeArgTypes = rawMethod.arguments.map(asmType)
                val bridgeReturnType = asmType(rawMethod.returnType)
                val gen = MethodEmitter.newGenerator(cw, access, rawMethod.name, bridgeReturnType, bridgeArgTypes)

                val ownerType = AsmUtil.objectType(classDef.name)
                gen.loadThis()
                var i = 0
                while i < bridgeArgTypes.length do
                  gen.loadArg(i)
                  val desired = asmType(impl.arguments(i))
                  val provided = bridgeArgTypes(i)
                  if desired != provided then
                    if isReferenceAsmType(provided) && !isReferenceAsmType(desired) then
                      gen.unbox(desired)
                    else if !isReferenceAsmType(provided) && isReferenceAsmType(desired) then
                      gen.valueOf(provided)
                    else if isReferenceAsmType(desired) then
                      gen.checkCast(desired)
                    else
                      gen.cast(provided, desired)
                  i += 1

                gen.invokeVirtual(ownerType, AsmMethod(impl.name, implDesc))
                val implRet = asmType(impl.returnType)
                if implRet != AsmType.VOID_TYPE && !isReferenceAsmType(implRet) && isReferenceAsmType(bridgeReturnType) then
                  gen.valueOf(implRet)
                gen.returnValue()
                gen.endMethod()
          }

  private def isCovariantReturn(inherited: TypedAST.Type, own: TypedAST.Type): Boolean =
    !inherited.isBasicType && !own.isBasicType && TypeRules.isSuperType(inherited, own)

  private def isBridgeCompatible(implArg: TypedAST.Type, specializedArg: TypedAST.Type): Boolean = {
    if implArg.name == specializedArg.name then return true
    // Allow primitive implementation argument to match boxed specialized arg
    // (e.g. Int vs Integer) and vice versa.
    (implArg, specializedArg) match
      case (bt: TypedAST.BasicType, ct: TypedAST.ClassType) if bt != TypedAST.BasicType.VOID =>
        ct.name == boxedName(bt)
      case (ct: TypedAST.ClassType, bt: TypedAST.BasicType) if bt != TypedAST.BasicType.VOID =>
        ct.name == boxedName(bt)
      case _ => false
  }

  private def boxedName(bt: TypedAST.BasicType): String = bt match {
    case TypedAST.BasicType.BOOLEAN => "java.lang.Boolean"
    case TypedAST.BasicType.BYTE    => "java.lang.Byte"
    case TypedAST.BasicType.SHORT   => "java.lang.Short"
    case TypedAST.BasicType.CHAR    => "java.lang.Character"
    case TypedAST.BasicType.INT     => "java.lang.Integer"
    case TypedAST.BasicType.LONG    => "java.lang.Long"
    case TypedAST.BasicType.FLOAT   => "java.lang.Float"
    case TypedAST.BasicType.DOUBLE  => "java.lang.Double"
    case _ => ""
  }

  private def substituteTypeVars(tp: TypedAST.Type, subst: Map[String, TypedAST.Type]): TypedAST.Type = tp match
    case tv: TypeVariableType => subst.getOrElse(tv.name, tv)
    case at: ArrayType =>
      val newComponent = substituteTypeVars(at.component, subst)
      if newComponent eq at.component then at else at.table.loadArray(newComponent, at.dimension)
    case ap: AppliedClassType =>
      val newArgs = ap.typeArguments.map(substituteTypeVars(_, subst)).toList
      TypedAST.AppliedClassType(ap.raw, newArgs)
    case other => other

  // Every supertype, each with the substitution for its own type parameters
  // (empty for a non-generic one). A non-generic supertype still needs a bridge
  // when an override narrows its return type (`name(): String` for an inherited
  // `name(): Object`), as javac emits; without one, a call through the supertype
  // threw AbstractMethodError or ran the inherited method (#2386).
  private def collectSupertypes(classDef: ClassDefinition): List[(TypedAST.ClassType, Map[String, TypedAST.Type])] =
    val visitedApplied = mutable.HashSet[AppliedClassType]()
    val visitedRaw = mutable.HashSet[TypedAST.ClassType]()
    val out = mutable.ArrayBuffer[(TypedAST.ClassType, Map[String, TypedAST.Type])]()

    def traverse(tp: TypedAST.ClassType, subst: Map[String, TypedAST.Type]): Unit =
      if tp == null then return
      tp match
        case applied0: AppliedClassType =>
          val specializedArgs = applied0.typeArguments.map(substituteTypeVars(_, subst)).toList
          val applied = TypedAST.AppliedClassType(applied0.raw, specializedArgs)
          if !visitedApplied.contains(applied) then
            visitedApplied += applied
            val nextSubst: Map[String, TypedAST.Type] =
              applied.raw.typeParameters.map(_.name).zip(applied.typeArguments).toMap
            out += ((applied.raw, nextSubst))
            traverse(applied.raw.superClass, nextSubst)
            applied.raw.interfaces.foreach(traverse(_, nextSubst))

        case raw =>
          if !visitedRaw.contains(raw) then
            visitedRaw += raw
            if raw.typeParameters.isEmpty then out += ((raw, Map.empty))
            val nextSubst = if raw.typeParameters.nonEmpty then Map.empty[String, TypedAST.Type] else subst
            traverse(raw.superClass, nextSubst)
            raw.interfaces.foreach(traverse(_, nextSubst))

    traverse(classDef.superClass, Map.empty)
    classDef.interfaces.foreach(traverse(_, Map.empty))
    out.toList
}
