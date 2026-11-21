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
    // For each inherited (name, parameter descriptor) slot: a method declaring it, and
    // the method whose code runs for it (None = abstract). The nearest superclass
    // decides; an interface decides only a slot no superclass declares. A superclass's
    // own `?` bridge counts: in `B extends A` with `override def f(x: Int?)` over
    // `A.f(Int)`, both slots run `B.f(Int?)`, so they are one slot to a subclass.
    val inheritedSlots = inheritedOwners(classDef)

    // Whether `m` (own slot `own`) takes over the `?`-different slot `target` with a
    // bridge (#2896's table):
    // - `target` has no code: the bridge is its only code (rows 2 and 4);
    // - `target` and `own` run the same inherited method: overriding one is
    //   overriding both (a chain of `?` overrides);
    // - both run different methods: no bridge, `target` keeps its own (row 3);
    // - only `target` has code: a bridge, `override` or not. Differing only by `?`
    //   is the same slot, so `override` stays a check and never decides what runs.
    //   Except in the unboxing direction (own `f(Int)` over an inherited `f(Int?)`): a
    //   `null` through the base would throw, so that stays as it was (row 10).
    def takesOverSlot(m: TypedAST.Method, targetMethod: TypedAST.Method, own: String, target: String): Boolean =
      takesOver(m, targetMethod, inheritedSlots.get((m.name, target)).flatMap(_._2), inheritedSlots.get((m.name, own)).flatMap(_._2))

    for (sourceRaw, subst) <- sources do
      def substituteClassParams(tp: TypedAST.Type): TypedAST.Type = substituteTypeVars(tp, subst)

      for rawMethod <- sourceRaw.methods do
        // A final slot is never bridged: the JVM rejects the class (typing reports E0039).
        if Modifier.isStatic(rawMethod.modifier) || Modifier.isPrivate(rawMethod.modifier) || Modifier.isFinal(rawMethod.modifier) then
          ()
        else
          val sameName = ownByName.getOrElse(rawMethod.name, Nil)
          val specializedArgs = if sameName.isEmpty then null else rawMethod.arguments.map(substituteClassParams)
          // A static method never implements an instance slot (#2927).
          val candidates = if sameName.isEmpty then Nil else sameName.filter { m =>
            !Modifier.isStatic(m.modifier) && m.arguments.length == specializedArgs.length && (
              if subst.isEmpty then
                // A non-generic supertype: a covariant return override, as in
                // Java, or a parameter that differs only by `?` (`k: Int?` for an
                // inherited `k: Int`, #2896). `foo(x: JInteger)` beside an inherited
                // `foo(x: Int)` is an overload, so arguments must match up to `?`.
                m.arguments.indices.forall(i => plain(m.arguments(i)).name == plain(specializedArgs(i)).name) &&
                isBridgeableReturn(rawMethod.returnType, m.returnType) && {
                  // Whether a `?` bridge takes over the target slot: `takesOverSlot`.
                  val own = paramDesc(m.arguments)
                  val target = paramDesc(rawMethod.arguments)
                  own == target || takesOverSlot(m, rawMethod, own, target)
                }
              else
                m.arguments.indices.forall(i => isBridgeCompatible(m.arguments(i), specializedArgs(i)))
            )
          }
          // The override is the overload whose parameters are the substituted type
          // arguments (`n(x: JInteger)` for `n(x: T)` at `T = JInteger`), as in
          // Java; a primitive one (`n(x: Int)`) is bridged to only when no closer
          // candidate exists (#2559).
          val implOpt =
            if candidates.isEmpty then None
            else
              def exactCount(m: TypedAST.Method): Int =
                m.arguments.indices.count(i => plain(m.arguments(i)).name == plain(specializedArgs(i)).name)
              val best = candidates.reduceLeft((a, b) => if exactCount(b) > exactCount(a) then b else a)
              // A generic supertype's slot is implemented by an inherited exact override
              // too (`Base.n(x: JInteger)` for `G[JInteger].n(x: T)`), which beats an own
              // overload that only matches by boxing (`n(x: Int)`), as in Java (#2680).
              if subst.nonEmpty && exactCount(best) < specializedArgs.length then
                inheritedExact(classDef, rawMethod.name, specializedArgs).orElse(Some(best))
              else Some(best)

          implOpt.foreach { impl =>
            // In an interface only a default method (one with a body) has code to bridge to.
            val hasNoBody = classDef.isInterface && impl.asInstanceOf[MethodDefinition].getBlock == null
            if Modifier.isAbstract(impl.modifier) || Modifier.isStatic(impl.modifier) || hasNoBody then
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

                if classDef.isInterface then gen.invokeInterface(ownerType, AsmMethod(impl.name, implDesc))
                else gen.invokeVirtual(ownerType, AsmMethod(impl.name, implDesc))
                val implRet = asmType(impl.returnType)
                if implRet != AsmType.VOID_TYPE && !isReferenceAsmType(implRet) && isReferenceAsmType(bridgeReturnType) then
                  gen.valueOf(implRet)
                gen.returnValue()
                gen.endMethod()
          }

  // The nearest concrete superclass method with exactly these parameter types, if any.
  private def inheritedExact(classDef: ClassDefinition, name: String, args: Array[TypedAST.Type]): Option[TypedAST.Method] =
    var sup: TypedAST.ClassType = if classDef.isInterface then null else classDef.superClass
    while sup != null do
      val hit = rawOf(sup).methods.find { m =>
        m.name == name && isVisibleInstance(m) && !Modifier.isAbstract(m.modifier) &&
          m.arguments.length == args.length &&
          m.arguments.indices.forall(i => plain(m.arguments(i)).name == plain(args(i)).name)
      }
      if hit.isDefined then return hit
      sup = rawOf(sup).superClass
    None

  private type Slot = (String, String)
  // A slot's declaring method, and the method whose code runs for it (None = abstract).
  private type SlotOwners = Map[Slot, (TypedAST.Method, Option[TypedAST.Method])]

  private def paramDesc(args: Array[TypedAST.Type]): String = args.map(asmType(_).getDescriptor).mkString("(", "", ")")

  private def isVisibleInstance(m: TypedAST.Method): Boolean =
    !Modifier.isStatic(m.modifier) && !Modifier.isPrivate(m.modifier)

  private def rawOf(t: TypedAST.ClassType): TypedAST.ClassType = t match
    case applied: AppliedClassType => applied.raw
    case other => other

  private def takesOver(
    m: TypedAST.Method, target: TypedAST.Method,
    targetOwner: Option[TypedAST.Method], ownOwner: Option[TypedAST.Method]
  ): Boolean =
    targetOwner match
      case None => true
      case Some(t) => ownOwner match
        // A chain also obeys row 10: an own `f(Int)` never takes a concrete `f(Int?)`
        // slot with a `void`/primitive return, even when a superclass's `?` bridge
        // joined the two slots (`Base.f(Int)`, `Mid.f(Int?)`, `Sub.f(Int)`): a `null`
        // through `Mid` would reach `Sub.f(Int)` and throw.
        case Some(o) => (o eq t) && !keepsBaseSlot(m, target)
        case None => !keepsBaseSlot(m, target)

  // Row 10 of #2896's table: an own `f(Int)` (no code inherited for it) beside an
  // inherited concrete `f(Int?)` takes the slot only as it did before the `?` rule, with
  // a reference return (a covariant override). With a `void` or primitive return it is
  // an overload: a bridge would unbox, and a `null` through the base would throw (#3053).
  private[asm] def keepsBaseSlot(m: TypedAST.Method, target: TypedAST.Method): Boolean =
    unboxes(m.arguments, target.arguments) && !isCovariantReturn(target.returnType, m.returnType)

  // Some parameter is a primitive where the inherited one is its boxed `?` form.
  private def unboxes(own: Array[TypedAST.Type], inherited: Array[TypedAST.Type]): Boolean =
    own.indices.exists(i => isPrimitive(asmType(own(i))) && !isPrimitive(asmType(inherited(i))))

  private def isPrimitive(t: AsmType): Boolean = t.getSort != AsmType.OBJECT && t.getSort != AsmType.ARRAY

  private val ownersMemo = mutable.HashMap[TypedAST.ClassType, SlotOwners]()

  // What an instance of `superClass` runs for `m`'s slot, when that is a `?` bridge's
  // target (a method with other parameter types): `AsmCodeGeneration.superCallTarget`.
  def superSlotTarget(superClass: TypedAST.ClassType, m: TypedAST.Method): Option[TypedAST.Method] =
    if superClass == null || superClass.isInterface then None
    else
      val desc = paramDesc(m.arguments)
      ownersOf(superClass).get((m.name, desc)).flatMap(_._2).filter(x => paramDesc(x.arguments) != desc)

  // The slots `cls` inherits: from its superclass chain (with the bridges each level
  // emits), then from its interfaces for any slot no superclass declares.
  private def inheritedOwners(cls: TypedAST.ClassType): SlotOwners =
    val out = mutable.LinkedHashMap.from(if cls.isInterface || cls.superClass == null then Map.empty else ownersOf(cls.superClass))
    val seen = mutable.HashSet[TypedAST.ClassType]()
    def addInterfaces(t: TypedAST.ClassType): Unit =
      val raw = rawOf(t)
      if seen.add(raw) then
        raw.methods.iterator.filter(isVisibleInstance).foreach { m =>
          val slot = (m.name, paramDesc(m.arguments))
          if !out.contains(slot) then out(slot) = (m, if Modifier.isAbstract(m.modifier) then None else Some(m))
        }
        raw.interfaces.foreach(addInterfaces)
    cls.interfaces.foreach(addInterfaces)
    out.toMap

  // The slots of an instance of `cls`: inherited ones, its own methods, and the `?`
  // bridges it emits (the same rule as `emitBridges` for a non-generic supertype).
  private def ownersOf(cls0: TypedAST.ClassType): SlotOwners =
    val cls = rawOf(cls0)
    ownersMemo.get(cls) match
      case Some(done) => done
      case None =>
        val inherited = inheritedOwners(cls)
        val out = mutable.LinkedHashMap.from(inherited)
        val own = cls.methods.filter(isVisibleInstance)
        val ownSlots = own.map(m => (m.name, paramDesc(m.arguments))).toSet
        own.foreach { m => out((m.name, paramDesc(m.arguments))) = (m, if Modifier.isAbstract(m.modifier) then None else Some(m)) }
        for
          m <- own if !Modifier.isAbstract(m.modifier)
          ownSlot = (m.name, paramDesc(m.arguments))
          (slot, (sample, targetOwner)) <- inherited
          if slot._1 == m.name && !ownSlots.contains(slot) && sample.arguments.length == m.arguments.length
          if m.arguments.indices.forall(i => plain(m.arguments(i)).name == plain(sample.arguments(i)).name)
          if isBridgeableReturn(sample.returnType, m.returnType)
          if !Modifier.isFinal(sample.modifier)
          if takesOver(m, sample, targetOwner, inherited.get(ownSlot).flatMap(_._2))
        do out(slot) = (sample, Some(m))
        val result = out.toMap
        ownersMemo(cls) = result
        result

  // `T?` is the same JVM type as `T`; nullability does not change an override (#2719).
  private def plain(tp: TypedAST.Type): TypedAST.Type = tp match
    case nt: NullableType => nt.unwrap
    case other => other

  private def isCovariantReturn(inherited: TypedAST.Type, own: TypedAST.Type): Boolean =
    !inherited.isBasicType && !own.isBasicType && TypeRules.isSuperType(inherited, own)

  // The bridge returns what the inherited method returns: the same JVM type
  // (`void`, `int`, ...) or a covariant reference.
  private def isBridgeableReturn(inherited: TypedAST.Type, own: TypedAST.Type): Boolean =
    asmType(inherited) == asmType(own) || isCovariantReturn(inherited, own)

  private def isBridgeCompatible(implArg: TypedAST.Type, specializedArg: TypedAST.Type): Boolean = {
    if plain(implArg).name == plain(specializedArg).name then return true
    // Allow primitive implementation argument to match boxed specialized arg
    // (e.g. Int vs Integer) and vice versa.
    (plain(implArg), plain(specializedArg)) match
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
    case nt: NullableType => NullableType.of(substituteTypeVars(nt.unwrap, subst))
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

    // An interface's superClass is Object, whose methods no default method overrides.
    if !classDef.isInterface then traverse(classDef.superClass, Map.empty)
    classDef.interfaces.foreach(traverse(_, Map.empty))
    out.toList
}
