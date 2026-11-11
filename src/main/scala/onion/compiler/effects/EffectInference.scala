package onion.compiler.effects

import onion.compiler.{Modifier, TypedAST}
import onion.compiler.TypedAST._

import scala.collection.mutable

/**
 * Transitive effect inference over the typed AST (issue #356).
 *
 * For every method and constructor defined in the compiled sources, computes the set of
 * [[Effect]]s its body can perform: the union of the effect table's verdicts for every
 * external call it makes, joined transitively through the user-defined methods it calls.
 * Recursion and mutual recursion are handled by a fixed point.
 *
 * Two deliberate over-approximations, both sound for boundary checking (#357):
 *
 *   - A closure body's effects are charged to the method that *creates* the closure,
 *     not the one that eventually invokes it (invoking a function value is `pure` in
 *     the table). Creation is the only site the checker can always see.
 *   - A call that neither the table nor the compiled sources can account for
 *     contributes [[Effect.Unknown]] — "not known pure", never silently dropped.
 */
object EffectInference {

  /** Inference units are the bodies the compiled sources actually contain. */
  type Unit0 = AnyRef // MethodDefinition | ConstructorDefinition | ClassInit

  /**
   * A class's static initializer, as an inference unit of its own. It runs the first time
   * the class is used from outside, so a call, a field read or a `new` that reaches the class
   * from another class carries its effects (#2601). It is not a row of `--effects`.
   */
  final case class ClassInit(cls: ClassDefinition)

  /**
   * A program method that library code calls back on a value it was handed (`toString` from
   * string concatenation, `hashCode` from a map literal, ...). It carries the method's
   * effects except `mutate`: what such a method changes is its own business, and only the
   * outside world is what a caller must hear about (#2601).
   */
  final case class Callback(method: MethodDefinition)

  private def effectsOf(result: collection.Map[Unit0, Set[Effect]], d: Unit0): Set[Effect] = d match {
    case Callback(md) => result.getOrElse(md, Set.empty) - Effect.Mutate
    case other        => result.getOrElse(other, Set.empty)
  }

  final case class MethodEffects(
    className: String,
    methodName: String,
    argumentTypes: Seq[String],
    effects: Set[Effect]
  ) {
    def render: String =
      s"$className#$methodName(${argumentTypes.mkString(", ")}): ${Effect.render(effects)}"
  }

  /** One direct call in a body: where it is, what it calls, and the callee's full
   *  effect set. `location` may be null for synthesized nodes — callers fall back. */
  /** `initializerOf` names the class when the site is the first use of a program class that runs its static initializer. */
  final case class CallSite(location: onion.compiler.Location, callee: String, effects: Set[Effect],
                            initializerOf: Option[String] = None)

  /** Per-method rendering for `--effects`. */
  def infer(classes: Seq[ClassDefinition],
            table: EffectTable.Table = EffectTable.builtin): Seq[MethodEffects] = {
    val (units, sets) = inferUnits(classes, table)
    // A synthetic accessor (`super.f()` in a lambda) still carries its effects into the method
    // that calls it, but it is not a method the program wrote, so it is not listed.
    units.iterator.filterNot {
      case (md: MethodDefinition, _) => Modifier.isSynthetic(md.modifier)
      case _ => false
    }.map { case (u, (cls, name, args)) =>
      MethodEffects(cls, name, args, sets(u))
    }.toSeq
  }

  /**
   * The full result: unit descriptors in declaration order, and each unit's inferred
   * effect set. Exposed for [[onion.compiler.typing.CapabilityCheckPass]], which needs
   * to resolve callees by definition object.
   */
  def inferUnits(classes: Seq[ClassDefinition], table: EffectTable.Table = EffectTable.builtin,
                 callbacks: Boolean = false)
      : (mutable.LinkedHashMap[Unit0, (String, String, Seq[String])], Map[Unit0, Set[Effect]]) = {
    val units = mutable.LinkedHashMap[Unit0, (String, String, Seq[String])]()
    for (cd <- classes) {
      for (m <- cd.methods) m match {
        case md: MethodDefinition =>
          units(md) = (cd.name, md.name, md.arguments.toSeq.map(typeName))
        case _ =>
      }
      for (c <- cd.constructors) c match {
        case ctor: ConstructorDefinition =>
          units(ctor) = (cd.name, "<init>", ctor.getArgs.toSeq.map(typeName))
        case _ =>
      }
    }

    // Direct dependencies: external effects + edges into other units.
    val base = mutable.LinkedHashMap[Unit0, Set[Effect]]()
    val edges = mutable.LinkedHashMap[Unit0, Set[Unit0]]()
    for ((u, _) <- units) {
      val collector = walk(u, units.keySet, classes, table, callbacks)
      base(u) = collector.effects.toSet
      edges(u) = collector.internalCallees.toSet
    }
    for (cd <- classes if cd.staticInitializers.nonEmpty) {
      val init = ClassInit(cd)
      val collector = walk(init, units.keySet, classes, table, callbacks)
      base(init) = collector.effects.toSet
      edges(init) = collector.internalCallees.toSet
    }

    // Fixed point: propagate through user-to-user edges until stable.
    val result = mutable.LinkedHashMap[Unit0, Set[Effect]](base.toSeq: _*)
    var changed = true
    while (changed) {
      changed = false
      for ((u, deps) <- edges) {
        val merged = deps.foldLeft(result(u))((acc, d) => acc ++ effectsOf(result, d))
        if (merged.size != result(u).size) {
          result(u) = merged
          changed = true
        }
      }
    }
    for (deps <- edges.values; d <- deps) d match {
      case cb: Callback => result(cb) = effectsOf(result, cb)
      case _ =>
    }
    (units, result.toMap)
  }

  private val callbackCache =
    new java.util.WeakHashMap[Seq[ClassDefinition], Map[String, Seq[MethodDefinition]]]()

  /**
   * Per program class, its instance methods that override a method of a library type
   * (`toString`, `hashCode`, `equals`, `compareTo`, `iterator`, `run`, ...): the methods
   * library code can call back on a program value it was handed (#2601).
   */
  private[effects] def libraryCallbacks(classes: Seq[ClassDefinition]): Map[String, Seq[MethodDefinition]] =
    callbackCache.synchronized {
      val cached = callbackCache.get(classes)
      if (cached != null) cached
      else {
        val program = classes.map(_.name).toSet
        def libraryHas(c: TypedAST.ClassType, name: String, arity: Int, seen: mutable.Set[String]): Boolean =
          c != null && seen.add(c.name) && (
            if (program.contains(c.name)) libraryHas(c.superClass, name, arity, seen) || c.interfaces.exists(libraryHas(_, name, arity, seen))
            else c.methods(name).exists(_.arguments.length == arity) ||
              libraryHas(c.superClass, name, arity, seen) || c.interfaces.exists(libraryHas(_, name, arity, seen)))
        // `toString`, `hashCode` and `equals` come from `Object` even when no supertype is recorded.
        def fromObject(name: String, arity: Int): Boolean =
          (name == "toString" && arity == 0) || (name == "hashCode" && arity == 0) || (name == "equals" && arity == 1)
        val result = classes.map { cd =>
          cd.name -> cd.methods.collect {
            case md: MethodDefinition if (md.getBlock != null || isRecordObjectMethod(md)) && !Modifier.isStatic(md.modifier) && !Modifier.isPrivate(md.modifier) &&
              (fromObject(md.name, md.arguments.length) ||
                libraryHas(cd.superClass, md.name, md.arguments.length, mutable.Set()) ||
                cd.interfaces.exists(libraryHas(_, md.name, md.arguments.length, mutable.Set()))) => md
          }.toSeq
        }.filter(_._2.nonEmpty).toMap
        callbackCache.put(classes, result)
        result
      }
    }

  /**
   * A record's generated `toString`, `hashCode` or `equals`. It has no body to walk, but it
   * calls the same method on each reference component, so it runs whatever those overrides
   * run; or, when an interface the record conforms to has a default for it, it forwards to
   * that default (#2601).
   */
  private def isRecordObjectMethod(md: MethodDefinition): Boolean =
    md.getBlock == null && (md.modifier & (Modifier.SYNTHETIC_RECORD | Modifier.SYNTHETIC_INTERFACE_FORWARD)) != 0 && (md.affiliation match {
      case cd: ClassDefinition => cd.isRecord
      case _                   => false
    }) && ((md.name == "toString" && md.arguments.isEmpty) || (md.name == "hashCode" && md.arguments.isEmpty) ||
      (md.name == "equals" && md.arguments.length == 1))

  /** The class a unit's body belongs to; a reference to that class needs no initializer run. */
  private def ownerOf(unit: Unit0): ClassDefinition = unit match {
    case md: MethodDefinition    => md.affiliation match { case cd: ClassDefinition => cd; case _ => null }
    case cd: ConstructorDefinition => cd.affiliation match { case c: ClassDefinition => c; case _ => null }
    case ClassInit(cls)          => cls
    case _                       => null
  }

  /** A collector that has walked `unit`'s body. */
  private def walk(unit: Unit0, units: collection.Set[Unit0], classes: Seq[ClassDefinition],
                   table: EffectTable.Table, callbacks: Boolean = false): Collector = {
    val collector = new Collector(units, classes, table,
      inConstructor = unit.isInstanceOf[ConstructorDefinition], owner = ownerOf(unit),
      inClassInit = unit.isInstanceOf[ClassInit], callbacks = callbacks)
    unit match {
      case md: MethodDefinition =>
        if (md.getBlock != null) collector.statement(md.getBlock)
        else if (isRecordObjectMethod(md)) collector.recordObjectMethod(md)
      case ctor: ConstructorDefinition =>
        if (ctor.block != null) collector.statement(ctor.block)
        collector.superInit(ctor)
      case ClassInit(cls) =>
        cls.staticInitializers.foreach(collector.statement)
      case _ =>
    }
    collector
  }

  /**
   * Every direct call in `body`, with the callee's complete effect set — the table's
   * verdict for external callees, `unitEffects` for program-defined ones. Calls inside
   * closures the body creates are included (creation-site charging), which makes this
   * exactly the evidence set for a per-call-site capability diagnostic.
   */
  def callSites(body: ActionStatement, classes: Seq[ClassDefinition],
                unitEffects: Map[Unit0, Set[Effect]],
                table: EffectTable.Table = EffectTable.builtin,
                owner: ClassDefinition = null, callbacks: Boolean = false): Seq[CallSite] = {
    val collector = new Collector(unitEffects.keySet, classes, table, owner = owner, callbacks = callbacks)
    collector.statement(body)
    collector.rawSites.toSeq.map { raw =>
      CallSite(raw.location, raw.callee,
        raw.resolved.getOrElse(unitEffects.getOrElse(raw.internal, Set(Effect.Unknown))),
        raw.internal match { case ClassInit(cd) => Some(cd.name); case _ => None })
    }
  }

  /**
   * The effects of one expression, and the calls it makes. A contract clause is a single
   * `Term` rather than a method body (RFC #2557); `unitEffects` is the whole program's
   * inference, so a call to a program-defined method carries that method's effects.
   * Each call site is returned with the callee's full effect set, to name the culprit.
   */
  def termEffects(term: Term, classes: Seq[ClassDefinition],
                  unitEffects: Map[Unit0, Set[Effect]],
                  table: EffectTable.Table = EffectTable.builtin,
                  owner: ClassDefinition = null, callbacks: Boolean = false): (Set[Effect], Seq[CallSite]) = {
    val collector = new Collector(unitEffects.keySet, classes, table, owner = owner, callbacks = callbacks)
    collector.term(term)
    val sites = collector.rawSites.toSeq.map { raw =>
      CallSite(raw.location, raw.callee,
        raw.resolved.getOrElse(unitEffects.getOrElse(raw.internal, Set(Effect.Unknown))))
    }
    // A write inside the term has no call site; the collector's own set has it.
    (collector.effects.toSet ++ sites.flatMap(_.effects), sites)
  }

  /**
   * Every `Term`/`ActionStatement` node the Collector's match arms handle, by simple
   * name. `EffectWalkerCompletenessSpec` scans `TypedAST.scala` and fails when a node
   * exists that is not in this set — the drift guard for the no-silently-dropped-effect
   * property. Keep this list and the match arms below in step.
   */
  val handledNodes: Set[String] = Set(
    // statements
    "StatementBlock", "ExpressionActionStatement", "IfStatement", "ConditionalLoop",
    "Return", "Throw", "Synchronized", "Try", "Break", "Continue", "NOP",
    // terms with children
    "Call", "SafeCall", "SafeStaticExtensionCall", "CallStatic", "CallSuper", "NewObject", "NewClosure",
    "Begin", "BinaryTerm", "UnaryTerm", "ArrayLength", "RefArray", "SafeRefArray",
    "SetArray", "NonNullAssert", "AsInstanceOf", "InstanceOf", "StatementTerm",
    "SynchronizedTerm", "RefField", "SafeFieldAccess", "SetField", "SetStaticField",
    "SetLocal", "ListLiteral", "MapLiteral", "NewArray", "NewArrayWithValues",
    // leaves
    "RefLocal", "RefStaticField", "This", "OuterThis", "DefaultArgument",
    "BoolValue", "ByteValue", "ShortValue", "IntValue", "LongValue",
    "FloatValue", "DoubleValue", "CharacterValue", "StringValue", "NullValue"
  )

  private def typeName(t: TypedAST.Type): String = t match {
    case null                  => "?"
    case a: TypedAST.ArrayType => typeName(a.component) + "[]"
    case other                 => other.name
  }

  /** A recorded call: resolved effects for external callees, the unit for internal.
   *  `className`/`member`/`target`/`args`/`depth` are kept for [[StaticOperands]]: the
   *  callee's class, the called method (null for a constructor), the receiver (null
   *  for a static call or constructor), the argument terms, and how many closures deep
   *  the call sits in its body. */
  private final case class RawSite(location: onion.compiler.Location, callee: String,
                                   resolved: Option[Set[Effect]], internal: Unit0,
                                   className: String = null, member: TypedAST.Method = null,
                                   target: Term = null, args: Seq[Term] = Nil, depth: Int = 0,
                                   tableOperands: Seq[(Effect, String)] = Nil)

  /**
   * One external (table-classified) call in a body, with what [[StaticOperands]] needs
   * to read its operand: the receiver, the argument terms, and the closure depth.
   * `method` is null for a constructor call (`methodName` is then `<init>`).
   * `tableOperands` are the fixed operands the effect table attaches to the callee
   * (`Class#m=net:api.example.com`), shown by `--plan` like a literal argument.
   */
  final case class ExternalSite(className: String, methodName: String, method: TypedAST.Method,
                                target: Term, args: Seq[Term], depth: Int, effects: Set[Effect],
                                tableOperands: Seq[(Effect, String)] = Nil)

  /**
   * Syntactic facts about one body, for [[StaticOperands]]:
   *
   *   - `sites`          — every external call, in walk order
   *   - `localWrites`    — every assignment to a local of the body's OWN frame, as
   *                        (index, value, closure depth the assignment sits at)
   *   - `unsitedEffects` — external effects the body has that no recorded site carries
   *                        (a super-constructor call, a node the walker does not know)
   *   - `callees`        — program-defined methods/constructors it calls, in order
   */
  final case class BodyFacts(sites: Seq[ExternalSite], localWrites: Seq[(Int, Term, Int)],
                             unsitedEffects: Set[Effect], callees: Seq[Unit0])

  /** [[BodyFacts]] for one inference unit; `units` is the set of program-defined bodies. */
  def bodyFacts(unit: Unit0, units: collection.Set[Unit0], classes: Seq[ClassDefinition],
                table: EffectTable.Table = EffectTable.builtin): BodyFacts = {
    val collector = walk(unit, units, classes, table)
    val sites = collector.rawSites.iterator.collect {
      case r if r.resolved.isDefined =>
        val methodName = if (r.member == null) "<init>" else r.member.name
        ExternalSite(r.className, methodName, r.member, r.target, r.args, r.depth, r.resolved.get,
          r.tableOperands)
    }.toSeq
    BodyFacts(sites, collector.localWrites.toSeq, collector.unsitedEffects.toSet,
      collector.internalCallees.toSeq.map { case Callback(md) => md; case other => other }.distinct)
  }

  /**
   * Collects, from one body, the external effects, the internal callees, and the raw
   * call sites. Walks every `Term`/`ActionStatement` node the typed AST has;
   * `EffectWalkerCompletenessSpec` asserts the arms stay in sync with `TypedAST`,
   * because a missed node here means a silently dropped effect.
   */
  private final class Collector(units: collection.Set[Unit0], classes: Seq[ClassDefinition],
                                table: EffectTable.Table, inConstructor: Boolean = false,
                                owner: ClassDefinition = null, inClassInit: Boolean = false,
                                callbacks: Boolean = false) {
    val effects = mutable.Set[Effect]()
    val internalCallees = mutable.LinkedHashSet[Unit0]()
    val rawSites = mutable.Buffer[RawSite]()
    // For StaticOperands only; neither inference nor the capability check reads these.
    val localWrites = mutable.Buffer[(Int, Term, Int)]()
    val unsitedEffects = mutable.Set[Effect]()
    private var depth = 0

    // Typed call terms are mostly built without locations (convenience constructors
    // throughout typing), but many statements keep theirs. Track the nearest located
    // node seen so a call site can still be pinned to its statement's line.
    private var currentLoc: onion.compiler.Location = null
    private def here(nodeLoc: onion.compiler.Location): onion.compiler.Location =
      if (nodeLoc != null) nodeLoc else currentLoc
    private def noteLoc(nodeLoc: onion.compiler.Location): scala.Unit =
      if (nodeLoc != null) currentLoc = nodeLoc

    private val userClasses: Map[String, ClassDefinition] =
      classes.map(c => c.name -> c).toMap

    /**
     * The first use of a program class from another class runs its static initializer, so
     * the use carries the initializer's effects (#2601). Uses from inside the class itself
     * come after the class is initialized, and need nothing.
     */
    private def initializes(cls: TypedAST.ObjectType, location: onion.compiler.Location): scala.Unit =
      if (cls != null) for (cd <- programChain(cls.name) if cd.staticInitializers.nonEmpty && !ownerInitialized(cd.name)) {
        val init = ClassInit(cd)
        if (internalCallees.add(init))
          rawSites += RawSite(location, s"${cd.name} (static initializer)", None, init)
      }

    /** `name`'s program class and each program superclass: the JVM initializes a superclass first. */
    private def programChain(name: String): Seq[ClassDefinition] = {
      val out = mutable.Buffer[ClassDefinition]()
      var next = userClasses.get(name)
      while (next.isDefined && !out.exists(_ eq next.get)) {
        out += next.get
        next = Option(next.get.superClass).flatMap(sc => userClasses.get(sc.name))
      }
      out.toSeq
    }

    /** The owner and its superclasses are initialized before the owner's own code runs. */
    private lazy val ownerChainNames: Set[String] =
      if (owner == null) Set.empty else programChain(owner.name).map(_.name).toSet
    private def ownerInitialized(name: String): Boolean = ownerChainNames.contains(name)

    private lazy val libraryCallbacks: Map[String, Seq[MethodDefinition]] =
      EffectInference.libraryCallbacks(classes).map { case (k, ms) => k -> ms.filter(units.contains) }

    /** The program classes `v` can be an instance of; a cast (string concatenation casts to `Object`) narrows nothing. */
    private def valueClasses(v: Term): Seq[ClassDefinition] = v match {
      case a: AsInstanceOf if a.target != null =>
        val outer = compatible(a.`type`).toSet
        valueClasses(a.target).filter(outer.contains)
      case _ => compatible(v.`type`)
    }

    /** `append(x)` on a chain that starts at `new StringBuilder()`, as interpolation lowers to. */
    private def freshBuilderAppend(c: Call): Boolean =
      c.method != null && c.method.name == "append" && c.parameters.length == 1 &&
        c.method.affiliation != null && c.method.affiliation.name == "java.lang.StringBuilder" &&
        toStringOnlyAppend(c.method) && freshBuilder(c.target)

    /**
     * The `append` overloads that call nothing on their argument but `toString`: a primitive,
     * `String` or `Object` parameter. `append(CharSequence)` also calls `length()` and
     * `charAt()`, which a program class can override, so it stays an ordinary library call
     * (panel on #2815).
     */
    private def toStringOnlyAppend(m: TypedAST.Method): Boolean =
      m.arguments.length == 1 && m.arguments(0) != null && {
        val p = m.arguments(0)
        p.isBasicType || p.name == "java.lang.Object" || p.name == "java.lang.String"
      }

    private def freshBuilder(t: Term): Boolean = t match {
      case n: NewObject => n.parameters.isEmpty && n.constructor.affiliation != null &&
        n.constructor.affiliation.name == "java.lang.StringBuilder"
      case c: Call      => freshBuilderAppend(c)
      case _            => false
    }

    /** What an operator or a map literal can call on an object operand: `+` `toString`, `==` `equals`, `<` `compareTo`, a key `hashCode`. */
    private val operatorCallbacks = Set("toString", "equals", "compareTo", "hashCode")

    private val compatibleCache = mutable.Map[String, Seq[ClassDefinition]]()

    /** The program classes a value of type `tp` can be an instance of, including through type arguments. */
    private def compatible(tp: TypedAST.Type, seen: Set[TypedAST.Type] = Set.empty): Seq[ClassDefinition] =
      if (tp == null || seen.contains(tp)) Nil
      else tp match {
        case n: TypedAST.NullableType   => compatible(n.unwrap, seen + tp)
        case a: TypedAST.ArrayType      => compatible(a.component, seen + tp)
        case ap: TypedAST.AppliedClassType =>
          (compatible(ap.raw, seen + tp) ++ ap.typeArguments.toSeq.flatMap(compatible(_, seen + tp))).distinct
        case tv: TypedAST.TypeVariableType =>
          if (tv.upperBound == null) classes else compatible(tv.upperBound, seen + tp)
        case c: TypedAST.ClassType =>
          compatibleCache.getOrElseUpdate(c.name, classes.filter(cd => inherits(cd, c.name) || c.name == "java.lang.Object"))
        case _ => Nil
      }

    /**
     * Program values handed to library code can be called back: string concatenation calls
     * `toString`, a map literal `hashCode`, `==` `equals`, and any library method may call
     * what the value's class overrides. So the hand-off carries those overrides' effects
     * (#2601, rule 3 on the issue).
     *
     * This over-approximates (`ArrayList.add` never calls `toString`), which costs the build
     * only a predicate it does not run. A `tool`'s capability check or `--effects` would
     * instead report an effect the code does not have, so only the build turns it on.
     */
    private def toLibrary(values: Iterable[Term], location: onion.compiler.Location,
                          only: Set[String] = null): scala.Unit =
      if (callbacks && libraryCallbacks.nonEmpty)
        for (v <- values if v != null) handOff(valueClasses(v), location, only)

    /** Library code holds an instance of one of `owners`: it may call any library method they override. */
    private def handOff(owners: Seq[ClassDefinition], location: onion.compiler.Location,
                        only: Set[String] = null): scala.Unit =
      if (callbacks) for {
        cd <- owners
        md <- libraryCallbacks.getOrElse(cd.name, Nil) if only == null || only.contains(md.name)
      } if (internalCallees.add(Callback(md)))
          rawSites += RawSite(location,
            onion.compiler.toolbox.TypeFormatting.methodReference(md), None, Callback(md))

    /**
     * The program `close()` methods a try-with-resources can call on `resource`: those of
     * every program class the value can be an instance of. A library class's own `close()`
     * is not counted here, as before.
     */
    private def closes(resource: Term, location: onion.compiler.Location): scala.Unit =
      if (resource != null) for {
        cd <- compatible(resource.`type`)
        md <- cd.methods.collect {
          case m: MethodDefinition if m.name == "close" && m.arguments.isEmpty &&
            !Modifier.isStatic(m.modifier) && units.contains(m) => m
        }
      } if (internalCallees.add(md))
          rawSites += RawSite(location,
            onion.compiler.toolbox.TypeFormatting.methodReference(md), None, md)

    /**
     * The edges of a record's generated `toString`/`hashCode`/`equals`: the same method of
     * every program class a reference component can be an instance of, as a virtual call on
     * the component would reach. A nested record's generated method is one of them, so the
     * fixed point follows records inside records.
     */
    def recordObjectMethod(md: MethodDefinition): scala.Unit = md.affiliation match {
      case rec: ClassDefinition if (md.modifier & Modifier.SYNTHETIC_INTERFACE_FORWARD) != 0 =>
        val arity = md.arguments.length
        val seen = mutable.Set[String]()
        def defaults(c: TypedAST.ClassType): Seq[MethodDefinition] =
          if (c == null || !seen.add(c.name)) Nil
          else c.methods(md.name).toSeq.collect {
            case m: MethodDefinition if m.getBlock != null && units.contains(m) && m.arguments.length == arity => m
          } ++ c.interfaces.toSeq.flatMap(defaults)
        for (om <- rec.interfaces.toSeq.flatMap(defaults)) if (internalCallees.add(om))
          rawSites += RawSite(md.location,
            onion.compiler.toolbox.TypeFormatting.methodReference(om), None, om)
      case rec: ClassDefinition =>
        val arity = md.arguments.length
        for {
          (_, tp) <- rec.recordComponents.toSeq.flatMap(_.toSeq)
          if !tp.isInstanceOf[TypedAST.BasicType] && !tp.isInstanceOf[TypedAST.ArrayType]
          cd <- compatible(tp)
          om <- cd.methods.collect {
            case m: MethodDefinition if (m ne md) && units.contains(m) && m.name == md.name &&
              m.arguments.length == arity && !Modifier.isStatic(m.modifier) => m
          }
        } if (internalCallees.add(om))
            rawSites += RawSite(md.location,
              onion.compiler.toolbox.TypeFormatting.methodReference(om), None, om)
      case _ =>
    }

    /** Whether `cls` is `ancestor` or extends or conforms to it, by erased name. */
    private def inherits(cls: TypedAST.ClassType, ancestor: String): Boolean = {
      val seen = mutable.Set[String]()
      def go(c: TypedAST.ClassType): Boolean =
        c != null && seen.add(c.name) && (c.name == ancestor || go(c.superClass) || c.interfaces.exists(go))
      go(cls)
    }

    private val overrideCache = mutable.Map[(String, String, Int), Seq[MethodDefinition]]()

    /**
     * The program's own methods a virtual call to `m` can run instead of `m`: those of the
     * same name and arity in a class that extends or conforms to `m`'s class. A call through
     * an interface or an overridable method has the union of their effects (#2601).
     */
    private def overridesOf(m: TypedAST.Method): Seq[MethodDefinition] =
      if (m.affiliation == null || Modifier.isStatic(m.modifier) || Modifier.isPrivate(m.modifier)) Nil
      else {
        val arity = m.arguments.length
        overrideCache.getOrElseUpdate((m.affiliation.name, m.name, arity),
          classes.filter(cd => cd.name != m.affiliation.name && inherits(cd, m.affiliation.name)).flatMap { cd =>
            cd.methods.collect {
              case om: MethodDefinition if (om ne m) && units.contains(om) && om.name == m.name &&
                om.arguments.length == arity && !Modifier.isStatic(om.modifier) => om
            }
          })
      }

    private def method(m: TypedAST.Method, location: onion.compiler.Location,
                       target: Term, args: Array[Term]): scala.Unit = {
      // A top-level function is named as written, not through its synthetic owner.
      val callee = onion.compiler.toolbox.TypeFormatting.methodReference(m)
      if (Modifier.isStatic(m.modifier)) initializes(m.affiliation, location)
      m match {
        case md: MethodDefinition if units.contains(md) =>
          internalCallees += md
          rawSites += RawSite(location, callee, None, md)
        case _ =>
          // The compilation's table verdict (built-in + library tables), or Unknown.
          val eff = table.effectsOrUnknown(m.affiliation.name, m.name)
          effects ++= eff
          rawSites += RawSite(location, callee, Some(eff), null,
            m.affiliation.name, m, target, args.toSeq, depth, table.operands(m.affiliation.name, m.name))
          toLibrary(Option(target).toSeq ++ args.toSeq, location)
      }
      // A call on a receiver dispatches: any override the program defines may run instead.
      if (target != null) for (om <- overridesOf(m)) {
        internalCallees += om
        rawSites += RawSite(location,
          onion.compiler.toolbox.TypeFormatting.methodReference(om), None, om)
      }
    }

    private def constructor(c: ConstructorRef, location: onion.compiler.Location,
                            args: Array[Term]): scala.Unit = {
      val callee = s"new ${c.affiliation.name}"
      initializes(c.affiliation, location)
      programConstructor(c) match {
        case Some(cd) =>
          // Built on a library superclass, the new object reaches library code before the
          // program's constructor body runs; name its overrides here, where the call is.
          val chain = programChain(c.affiliation.name)
          if (chain.nonEmpty && Option(chain.last.superClass).exists(_.name != "java.lang.Object"))
            handOff(chain.take(1), location)
          internalCallees += cd
          rawSites += RawSite(location, callee, None, cd)
        case _ =>
          val eff = table.effectsOrUnknown(c.affiliation.name, "<init>")
          effects ++= eff
          rawSites += RawSite(location, callee, Some(eff), null,
            c.affiliation.name, null, null, args.toSeq, depth, table.operands(c.affiliation.name, "<init>"))
          toLibrary(args.toSeq, location)
      }
    }

    /** The program's own constructor behind `c`. `new Box[String]()` refers to it through a
     *  ref on the applied type, which is not the definition itself (#2601). */
    private def programConstructor(c: ConstructorRef): Option[ConstructorDefinition] = c match {
      case cd: ConstructorDefinition if units.contains(cd) => Some(cd)
      case _ => c.affiliation match {
        case applied: TypedAST.AppliedClassType =>
          applied.raw.constructors.collectFirst {
            case cd: ConstructorDefinition if units.contains(cd) && cd.getArgs.sameElements(c.getArgs) => cd
          }
        case _ => None
      }
    }

    /** The implicit or explicit `super(...)` call at the head of a constructor. */
    def superInit(ctor: ConstructorDefinition): scala.Unit = {
      val sup = ctor.superInitializer
      if (sup != null) {
        sup.terms.foreach(term)
        val superName = sup.classType.name
        userClasses.get(superName) match {
          case Some(cd) =>
            // Over-approximate: any of the user superclass's constructors.
            cd.constructors.foreach {
              case scd: ConstructorDefinition if units.contains(scd) => internalCallees += scd
              case other =>
                val eff = table.effectsOrUnknown(other.affiliation.name, "<init>")
                effects ++= eff; unsitedEffects ++= eff
            }
          case None =>
            val eff = table.effectsOrUnknown(superName, "<init>")
            effects ++= eff; unsitedEffects ++= eff
            // The library constructor receives `this` (`Exception()` calls `fillInStackTrace()`)
            // and its arguments, so it can call back whatever the object's class overrides (#2601).
            if (superName != "java.lang.Object") handOff(compatible(ctor.classType), ctor.location)
            toLibrary(sup.terms.toSeq, ctor.location)
        }
      }
    }

    def statement(s: ActionStatement): scala.Unit = { if (s != null) noteLoc(s.location); s match {
      case null => ()
      case b: StatementBlock            => b.statements.foreach(statement)
      case e: ExpressionActionStatement => term(e.term)
      case i: IfStatement               => term(i.condition); statement(i.thenStatement); statement(i.elseStatement)
      case l: ConditionalLoop           => term(l.condition); statement(l.stmt); statement(l.update)
      case r: Return                    => if (r.term != null) term(r.term)
      case t: Throw                     => term(t.term); toLibrary(Seq(t.term), here(t.location))
      case s2: Synchronized             => term(s2.term); statement(s2.statement)
      case t: Try =>
        t.resources.foreach { case (_, r) => term(r) }
        statement(t.tryStatement)
        // Leaving the block calls each resource's `close()`: a program class's own runs then.
        t.resources.foreach { case (_, r) => closes(r, here(t.location)) }
        t.catchStatements.foreach(statement)
        statement(t.finallyStatement)
      case _: Break | _: Continue | _: NOP => ()
      case _ =>
        // A statement this walker does not know cannot be vouched for. Never crash
        // over it (no-crash bar); EffectWalkerCompletenessSpec keeps this arm dead.
        effects += Effect.Unknown; unsitedEffects += Effect.Unknown
    }}

    def term(t: Term): scala.Unit = { if (t != null) noteLoc(t.location)
      // A call usually has no location of its own; pin it to the nearest located node seen
      // BEFORE its operands are walked, or the argument visited last would claim the site.
      val entry = currentLoc
      t match {
      case null => ()
      case c: Call if freshBuilderAppend(c) =>
        // String interpolation: appends to a builder the expression made itself, so no
        // memory anyone else can see changes. Like `+`, an object part may have its
        // `toString` called back (#2605).
        term(c.target); c.parameters.foreach(term)
        toLibrary(c.parameters.toSeq.filter(o => o.`type` != null && !o.`type`.isBasicType), entry, operatorCallbacks)
      case c: Call          => term(c.target); c.parameters.foreach(term); method(c.method, entry, c.target, c.parameters)
      case c: SafeCall      => term(c.target); c.parameters.foreach(term); method(c.method, entry, c.target, c.parameters)
      case c: SafeStaticExtensionCall => term(c.target); c.parameters.foreach(term); method(c.method, entry, null, Array.empty)
      case c: CallStatic    => c.parameters.foreach(term); method(c.method, entry, null, c.parameters)
      case c: CallSuper     =>
        term(c.target); c.params.foreach(term); method(c.method, entry, null, Array.empty)
        // `super.m()` into library code hands it `this`, which it can call back (#2601).
        c.method match {
          case md: MethodDefinition if units.contains(md) => ()
          case _ => toLibrary(Seq(c.target) ++ c.params, entry)
        }
      case n: NewObject     => n.parameters.foreach(term); constructor(n.constructor, entry, n.parameters)
      case n: NewClosure    =>
        // Absorbed at the creation site. The closure body is one frame deeper, which
        // is what `depth` tracks for the local-write bookkeeping below.
        depth += 1
        try statement(n.block) finally depth -= 1
      case b: Begin         => b.terms.foreach(term)
      case b: BinaryTerm    =>
        term(b.lhs); term(b.rhs)
        // `+` on a string, `==` and `<` on objects compile to library calls on the operands.
        toLibrary(Seq(b.lhs, b.rhs).filter(o => o.`type` != null && !o.`type`.isBasicType), entry, operatorCallbacks)
      case u: UnaryTerm     => term(u.operand)
      case a: ArrayLength   => term(a.target)
      case r: RefArray      => term(r.target); term(r.index)
      case r: SafeRefArray  => term(r.target); term(r.index)
      case s: SetArray      => term(s.target); term(s.index); term(s.value); effects += Effect.Mutate
      case n: NonNullAssert => term(n.target)
      case a: AsInstanceOf  => term(a.target)
      case i: InstanceOf    => term(i.target)
      case s: StatementTerm => statement(s.statement)
      case s: SynchronizedTerm => term(s.lock); term(s.body)
      case r: RefField      => term(r.target)
      case r: SafeFieldAccess => term(r.target)
      case s: SetField      =>
        term(s.target); term(s.value)
        // A constructor filling in its own `this` is initialisation: nobody else holds the
        // object yet, so `new C(..)` stays pure. Writing any other object is a mutation.
        if (!(inConstructor && s.target.isInstanceOf[This])) effects += Effect.Mutate
      case s: SetStaticField =>
        initializes(s.target, here(s.location)); term(s.value)
        // A static initializer filling in its own class's fields is initialisation, as a
        // constructor filling in `this` is: nobody can see the class before it finishes.
        if (!(inClassInit && owner != null && s.target != null && s.target.name == owner.name)) effects += Effect.Mutate
      case s: SetLocal      =>
        // A write whose relative frame equals the closure depth targets the body's own
        // frame (frame 0 from the body itself, 1 from a closure directly inside it, ...).
        if (s.frame == depth) localWrites += ((s.index, s.value, depth))
        term(s.value)
      case l: ListLiteral   => l.elements.foreach(term)
      case m: MapLiteral    => m.keys.foreach(term); m.values.foreach(term); toLibrary(m.keys.toSeq, entry, operatorCallbacks)
      case n: NewArray      => n.parameters.foreach(term)
      case n: NewArrayWithValues => n.values.foreach(term)
      case r: RefStaticField => initializes(r.target, here(r.location))
      case _: RefLocal | _: This | _: OuterThis => ()
      // An omitted argument runs its default method: the call has that method's effects.
      case d: DefaultArgument => method(d.method, entry, null, Array.empty)
      case _: BoolValue | _: ByteValue | _: ShortValue | _: IntValue | _: LongValue => ()
      case _: FloatValue | _: DoubleValue | _: CharacterValue | _: StringValue | _: NullValue => ()
      case _ =>
        // Same contract as the statement fallback: unknown, never a crash.
        effects += Effect.Unknown; unsitedEffects += Effect.Unknown
    }}
  }
}
