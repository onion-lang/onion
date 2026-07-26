package onion.compiler.effects

import onion.compiler.TypedAST
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
  type Unit0 = AnyRef // MethodDefinition | ConstructorDefinition

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
  final case class CallSite(location: onion.compiler.Location, callee: String, effects: Set[Effect])

  /** Per-method rendering for `--effects`. */
  def infer(classes: Seq[ClassDefinition]): Seq[MethodEffects] = {
    val (units, sets) = inferUnits(classes)
    units.iterator.map { case (u, (cls, name, args)) =>
      MethodEffects(cls, name, args, sets(u))
    }.toSeq
  }

  /**
   * The full result: unit descriptors in declaration order, and each unit's inferred
   * effect set. Exposed for [[onion.compiler.typing.CapabilityCheckPass]], which needs
   * to resolve callees by definition object.
   */
  def inferUnits(classes: Seq[ClassDefinition])
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
      val collector = new Collector(units.keySet, classes)
      u match {
        case md: MethodDefinition =>
          if (md.getBlock != null) collector.statement(md.getBlock)
        case ctor: ConstructorDefinition =>
          if (ctor.block != null) collector.statement(ctor.block)
          collector.superInit(ctor)
      }
      base(u) = collector.effects.toSet
      edges(u) = collector.internalCallees.toSet
    }

    // Fixed point: propagate through user-to-user edges until stable.
    val result = mutable.LinkedHashMap[Unit0, Set[Effect]](base.toSeq: _*)
    var changed = true
    while (changed) {
      changed = false
      for ((u, deps) <- edges) {
        val merged = deps.foldLeft(result(u))((acc, d) => acc ++ result.getOrElse(d, Set.empty))
        if (merged.size != result(u).size) {
          result(u) = merged
          changed = true
        }
      }
    }
    (units, result.toMap)
  }

  /**
   * Every direct call in `body`, with the callee's complete effect set — the table's
   * verdict for external callees, `unitEffects` for program-defined ones. Calls inside
   * closures the body creates are included (creation-site charging), which makes this
   * exactly the evidence set for a per-call-site capability diagnostic.
   */
  def callSites(body: ActionStatement, classes: Seq[ClassDefinition],
                unitEffects: Map[Unit0, Set[Effect]]): Seq[CallSite] = {
    val collector = new Collector(unitEffects.keySet, classes)
    collector.statement(body)
    collector.rawSites.toSeq.map { raw =>
      CallSite(raw.location, raw.callee,
        raw.resolved.getOrElse(unitEffects.getOrElse(raw.internal, Set(Effect.Unknown))))
    }
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
    "Call", "SafeCall", "CallStatic", "CallSuper", "NewObject", "NewClosure",
    "Begin", "BinaryTerm", "UnaryTerm", "ArrayLength", "RefArray", "SafeRefArray",
    "SetArray", "NonNullAssert", "AsInstanceOf", "InstanceOf", "StatementTerm",
    "SynchronizedTerm", "RefField", "SafeFieldAccess", "SetField", "SetStaticField",
    "SetLocal", "ListLiteral", "MapLiteral", "NewArray", "NewArrayWithValues",
    // leaves
    "RefLocal", "RefStaticField", "This", "OuterThis",
    "BoolValue", "ByteValue", "ShortValue", "IntValue", "LongValue",
    "FloatValue", "DoubleValue", "CharacterValue", "StringValue", "NullValue"
  )

  private def typeName(t: TypedAST.Type): String = t match {
    case null                  => "?"
    case a: TypedAST.ArrayType => typeName(a.component) + "[]"
    case other                 => other.name
  }

  /** A recorded call: resolved effects for external callees, the unit for internal. */
  private final case class RawSite(location: onion.compiler.Location, callee: String,
                                   resolved: Option[Set[Effect]], internal: Unit0)

  /**
   * Collects, from one body, the external effects, the internal callees, and the raw
   * call sites. Walks every `Term`/`ActionStatement` node the typed AST has;
   * `EffectWalkerCompletenessSpec` asserts the arms stay in sync with `TypedAST`,
   * because a missed node here means a silently dropped effect.
   */
  private final class Collector(units: collection.Set[Unit0], classes: Seq[ClassDefinition]) {
    val effects = mutable.Set[Effect]()
    val internalCallees = mutable.Set[Unit0]()
    val rawSites = mutable.Buffer[RawSite]()

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

    private def method(m: TypedAST.Method, location: onion.compiler.Location): scala.Unit = {
      val callee = s"${m.affiliation.name}::${m.name}"
      m match {
        case md: MethodDefinition if units.contains(md) =>
          internalCallees += md
          rawSites += RawSite(location, callee, None, md)
        case _ =>
          val eff = m.effects // table verdict, or Unknown
          effects ++= eff
          rawSites += RawSite(location, callee, Some(eff), null)
      }
    }

    private def constructor(c: ConstructorRef, location: onion.compiler.Location): scala.Unit = {
      val callee = s"new ${c.affiliation.name}"
      c match {
        case cd: ConstructorDefinition if units.contains(cd) =>
          internalCallees += cd
          rawSites += RawSite(location, callee, None, cd)
        case _ =>
          val eff = EffectTable.effectsOrUnknown(c.affiliation.name, "<init>")
          effects ++= eff
          rawSites += RawSite(location, callee, Some(eff), null)
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
              case other => effects ++= EffectTable.effectsOrUnknown(other.affiliation.name, "<init>")
            }
          case None =>
            effects ++= EffectTable.effectsOrUnknown(superName, "<init>")
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
      case t: Throw                     => term(t.term)
      case s2: Synchronized             => term(s2.term); statement(s2.statement)
      case t: Try =>
        t.resources.foreach { case (_, r) => term(r) }
        statement(t.tryStatement)
        t.catchStatements.foreach(statement)
        statement(t.finallyStatement)
      case _: Break | _: Continue | _: NOP => ()
      case _ =>
        // A statement this walker does not know cannot be vouched for. Never crash
        // over it (no-crash bar); EffectWalkerCompletenessSpec keeps this arm dead.
        effects += Effect.Unknown
    }}

    def term(t: Term): scala.Unit = { if (t != null) noteLoc(t.location); t match {
      case null => ()
      case c: Call          => term(c.target); c.parameters.foreach(term); method(c.method, here(c.location))
      case c: SafeCall      => term(c.target); c.parameters.foreach(term); method(c.method, here(c.location))
      case c: CallStatic    => c.parameters.foreach(term); method(c.method, here(c.location))
      case c: CallSuper     => term(c.target); c.params.foreach(term); method(c.method, here(c.location))
      case n: NewObject     => n.parameters.foreach(term); constructor(n.constructor, here(n.location))
      case n: NewClosure    => statement(n.block) // absorbed at the creation site
      case b: Begin         => b.terms.foreach(term)
      case b: BinaryTerm    => term(b.lhs); term(b.rhs)
      case u: UnaryTerm     => term(u.operand)
      case a: ArrayLength   => term(a.target)
      case r: RefArray      => term(r.target); term(r.index)
      case r: SafeRefArray  => term(r.target); term(r.index)
      case s: SetArray      => term(s.target); term(s.index); term(s.value)
      case n: NonNullAssert => term(n.target)
      case a: AsInstanceOf  => term(a.target)
      case i: InstanceOf    => term(i.target)
      case s: StatementTerm => statement(s.statement)
      case s: SynchronizedTerm => term(s.lock); term(s.body)
      case r: RefField      => term(r.target)
      case r: SafeFieldAccess => term(r.target)
      case s: SetField      => term(s.target); term(s.value)
      case s: SetStaticField => term(s.value)
      case s: SetLocal      => term(s.value)
      case l: ListLiteral   => l.elements.foreach(term)
      case m: MapLiteral    => m.keys.foreach(term); m.values.foreach(term)
      case n: NewArray      => n.parameters.foreach(term)
      case n: NewArrayWithValues => n.values.foreach(term)
      case _: RefLocal | _: RefStaticField | _: This | _: OuterThis => ()
      case _: BoolValue | _: ByteValue | _: ShortValue | _: IntValue | _: LongValue => ()
      case _: FloatValue | _: DoubleValue | _: CharacterValue | _: StringValue | _: NullValue => ()
      case _ =>
        // Same contract as the statement fallback: unknown, never a crash.
        effects += Effect.Unknown
    }}
  }
}
