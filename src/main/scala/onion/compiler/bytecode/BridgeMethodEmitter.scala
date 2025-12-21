package onion.compiler.bytecode

import onion.compiler.{AsmCodeGeneration, Modifier, TypedAST}
import onion.compiler.TypedAST.*
import org.objectweb.asm.{ClassWriter, Opcodes, Type => AsmType}
import org.objectweb.asm.commons.{Method => AsmMethod}

import scala.collection.mutable

final class BridgeMethodEmitter(private val codegen: AsmCodeGeneration) {
  import codegen.*

  def emitBridges(cw: ClassWriter, classDef: ClassDefinition): Unit =
    val sources = collectAppliedGenericSupertypes(classDef)
    if sources.isEmpty then return

    def methodDesc(ret: TypedAST.Type, args: Array[TypedAST.Type]): String =
      AsmType.getMethodDescriptor(asmType(ret), args.map(asmType)*)

    val existing = classDef.methods.map { m =>
      (m.name, methodDesc(m.returnType, m.arguments))
    }.toSet

    for source <- sources do
      val subst: Map[String, TypedAST.Type] =
        source.raw.typeParameters.map(_.name).zip(source.typeArguments).toMap

      def substituteClassParams(tp: TypedAST.Type): TypedAST.Type = substituteTypeVars(tp, subst)

      for rawMethod <- source.raw.methods do
        if Modifier.isStatic(rawMethod.modifier) || Modifier.isPrivate(rawMethod.modifier) then
          ()
        else
          val specializedArgs = rawMethod.arguments.map(substituteClassParams)
          val implOpt = classDef.methods.find { m =>
            m.name == rawMethod.name &&
            m.arguments.length == specializedArgs.length &&
            m.arguments.indices.forall(i => m.arguments(i).name == specializedArgs(i).name)
          }

          implOpt.foreach { impl =>
            if Modifier.isAbstract(impl.modifier) || Modifier.isStatic(impl.modifier) then
              ()
            else
              val bridgeDesc = methodDesc(rawMethod.returnType, rawMethod.arguments)
              val implDesc = methodDesc(impl.returnType, impl.arguments)
              if bridgeDesc != implDesc && !existing.contains((rawMethod.name, bridgeDesc)) then
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

  private def substituteTypeVars(tp: TypedAST.Type, subst: Map[String, TypedAST.Type]): TypedAST.Type = tp match
    case tv: TypeVariableType => subst.getOrElse(tv.name, tv)
    case at: ArrayType =>
      val newComponent = substituteTypeVars(at.component, subst)
      if newComponent eq at.component then at else at.table.loadArray(newComponent, at.dimension)
    case ap: AppliedClassType =>
      val newArgs = ap.typeArguments.map(substituteTypeVars(_, subst)).toList
      TypedAST.AppliedClassType(ap.raw, newArgs)
    case other => other

  private def collectAppliedGenericSupertypes(classDef: ClassDefinition): List[AppliedClassType] =
    val visitedApplied = mutable.HashSet[AppliedClassType]()
    val visitedRaw = mutable.HashSet[TypedAST.ClassType]()
    val out = mutable.ArrayBuffer[AppliedClassType]()

    def traverse(tp: TypedAST.ClassType, subst: Map[String, TypedAST.Type]): Unit =
      if tp == null then return
      tp match
        case applied0: AppliedClassType =>
          val specializedArgs = applied0.typeArguments.map(substituteTypeVars(_, subst)).toList
          val applied = TypedAST.AppliedClassType(applied0.raw, specializedArgs)
          if !visitedApplied.contains(applied) then
            visitedApplied += applied
            if applied.raw.typeParameters.nonEmpty then out += applied
            val nextSubst: Map[String, TypedAST.Type] =
              applied.raw.typeParameters.map(_.name).zip(applied.typeArguments).toMap
            traverse(applied.raw.superClass, nextSubst)
            applied.raw.interfaces.foreach(traverse(_, nextSubst))

        case raw =>
          if !visitedRaw.contains(raw) then
            visitedRaw += raw
            val nextSubst = if raw.typeParameters.nonEmpty then Map.empty[String, TypedAST.Type] else subst
            traverse(raw.superClass, nextSubst)
            raw.interfaces.foreach(traverse(_, nextSubst))

    traverse(classDef.superClass, Map.empty)
    classDef.interfaces.foreach(traverse(_, Map.empty))
    out.toList
}
