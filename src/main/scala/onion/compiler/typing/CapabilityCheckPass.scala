package onion.compiler.typing

import onion.compiler.{Location, SemanticError, Typing}
import onion.compiler.toolbox.Message
import onion.compiler.TypedAST._
import onion.compiler.effects.{Effect, EffectInference, StaticOperands}

/**
 * Pass 5 of typing (issue #357): the capability boundary.
 *
 * A `tool` declaration arrives here as an ordinary `MethodDefinition` whose annotations
 * carry the boundary — `onion.tool` plus one `requires:cap` entry per capability. The
 * pass infers the whole program's effect sets once, then checks each tool:
 *
 *   - '''E0077''' — the body performs an effect the requires clause does not declare.
 *     Reported at the call site that introduces it, naming both the effect and the
 *     callee; one report per (tool, effect), first witness wins.
 *   - '''E0078''' — a declared capability nothing in the body can perform.
 *   - '''E0079''' — a capability that is not an effect name, parameterizes an effect
 *     that takes no argument, or names a parameter the tool does not have.
 *
 * Effects are checked here and erased here: nothing below typing — optimizers, ASM
 * backend — sees any of this. A tool compiles to the same bytecode as the equivalent
 * function, which `ToolErasureSpec` pins.
 */
object CapabilityCheckPass {
  // Constants of the pass, not of a run: the regex in particular was compiled anew for
  // every compilation because it lived in the class body.
  private val ToolMarker = "onion.tool"
  private val RequiresPrefix = "requires:"
  private val CapabilityForm = """([A-Za-z]+)(?:\(([A-Za-z0-9_]+)\))?""".r
}

final class CapabilityCheckPass(typing: Typing) {
  import CapabilityCheckPass.*

  def run(classes: Seq[ClassDefinition]): Unit = {
    val tools: Seq[MethodDefinition] = for {
      cd <- classes
      m  <- cd.methods.toSeq
      md <- Some(m).collect { case md: MethodDefinition if md.annotations.contains(ToolMarker) => md }
    } yield md
    if (tools.isEmpty) return

    val (units, unitEffects) = EffectInference.inferUnits(classes)

    for (tool <- tools) {
      val declared = declaredCapabilities(tool)
      if (tool.getBlock != null) {
        checkUndeclared(tool, declared, classes, unitEffects)
        checkUnused(tool, declared, unitEffects)
      }
    }

    recordStaticOperands(tools, units.keySet, classes)
  }

  /**
   * FRICTION F11: adds each tool's statically known operands (literal hosts, commands,
   * env-var names, paths — see [[StaticOperands]]) to the contract the synthesized
   * `main` hands to `onion.ToolCli.dispatch`, so `--plan` can print them. The contract
   * is a string literal Rewriting built before any typing; this is the first point
   * where the operands are known, so the literal is replaced in place. The addition is
   * one extra key per tool, present only when something is known.
   */
  private def recordStaticOperands(tools: Seq[MethodDefinition],
                                   units: collection.Set[EffectInference.Unit0],
                                   classes: Seq[ClassDefinition]): Unit = {
    val fragments = tools.filter(_.getBlock != null).flatMap { tool =>
      StaticOperands.contractFragment(StaticOperands.forTool(tool, units, classes)).map(tool.name -> _)
    }
    if (fragments.isEmpty) return
    val toolClasses = tools.map(_.classType.name).toSet
    for {
      cd <- classes if toolClasses.contains(cd.name)
      m  <- cd.methods.toSeq
      md <- Some(m).collect { case md: MethodDefinition if md.name == "main" && md.getBlock != null => md }
      call <- findDispatch(md.getBlock)
    } {
      call.parameters(1) match {
        case sv: StringValue if sv.value != null && sv.value.startsWith("[{\"tool\":") =>
          val augmented = fragments.foldLeft(sv.value) { case (json, (name, fragment)) =>
            StaticOperands.insertIntoContract(json, name, fragment)
          }
          call.parameters(1) = new StringValue(sv.location, augmented, sv.`type`)
        case _ =>
      }
    }
  }

  /** The synthesized `onion.ToolCli.dispatch(args, contract)` call in a `main` body. */
  private def findDispatch(s: ActionStatement): Option[CallStatic] = s match {
    case b: StatementBlock            => b.statements.iterator.flatMap(findDispatch).nextOption()
    case e: ExpressionActionStatement => findDispatchTerm(e.term)
    case _                            => None
  }

  private def findDispatchTerm(t: Term): Option[CallStatic] = t match {
    case c: CallStatic if c.method != null && c.method.name == "dispatch"
        && c.method.affiliation != null && c.method.affiliation.name == "onion.ToolCli"
        && c.parameters.length == 2 => Some(c)
    case s: SetLocal      => findDispatchTerm(s.value)
    case b: Begin         => b.terms.iterator.flatMap(findDispatchTerm).nextOption()
    case s: StatementTerm => findDispatch(s.statement)
    case _                => None
  }

  /** Parses and validates the requires clause; invalid entries report E0079 and are
   *  dropped from the checked set so one mistake does not cascade. */
  private def declaredCapabilities(tool: MethodDefinition): Set[Effect] = {
    val paramNames = tool.argumentsWithDefaults.map(_.name).toSet
    val caps = tool.annotations.toSeq.filter(_.startsWith(RequiresPrefix)).map(_.stripPrefix(RequiresPrefix))
    val valid = Set.newBuilder[Effect]
    for (cap <- caps) cap match {
      case CapabilityForm(name, arg) =>
        Effect.parse(name) match {
          case None =>
            report(SemanticError.TOOL_BAD_CAPABILITY, tool.location, tool.name, cap,
              Message("error.semantic.toolCapability.notAnEffect", name, Effect.all.map(_.name).mkString(", ")))
          case Some(effect) =>
            if (arg != null && !Effect.parameterized.contains(effect))
              report(SemanticError.TOOL_BAD_CAPABILITY, tool.location, tool.name, cap,
                Message("error.semantic.toolCapability.takesNoArgument", name))
            else if (arg != null && !paramNames.contains(arg))
              report(SemanticError.TOOL_BAD_CAPABILITY, tool.location, tool.name, cap,
                Message("error.semantic.toolCapability.badParameter", arg))
            else valid += effect
        }
      case _ =>
        report(SemanticError.TOOL_BAD_CAPABILITY, tool.location, tool.name, cap,
          Message("error.semantic.toolCapability.notAnEffect", cap, Effect.all.map(_.name).mkString(", ")))
    }
    valid.result()
  }

  /** E0077 per effect, at the first call site that introduces it. */
  private def checkUndeclared(tool: MethodDefinition, declared: Set[Effect],
                              classes: Seq[ClassDefinition],
                              unitEffects: Map[EffectInference.Unit0, Set[Effect]]): Unit = {
    val reported = scala.collection.mutable.Set[Effect]()
    for (site <- EffectInference.callSites(tool.getBlock, classes, unitEffects)) {
      val missing = (site.effects -- declared) -- reported
      for (effect <- missing.toSeq.sorted) {
        reported += effect
        val where = if (site.location != null) site.location else tool.location
        report(SemanticError.TOOL_UNDECLARED_EFFECT, where, tool.name, effect.name, site.callee)
      }
    }
  }

  /** E0078: a declared capability the body cannot exercise. */
  private def checkUnused(tool: MethodDefinition, declared: Set[Effect],
                          unitEffects: Map[EffectInference.Unit0, Set[Effect]]): Unit = {
    val inferred = unitEffects.getOrElse(tool, Set.empty)
    for (cap <- (declared -- inferred).toSeq.sorted)
      report(SemanticError.TOOL_UNUSED_CAPABILITY, tool.location, tool.name, cap.name)
  }

  private def report(error: SemanticError, location: Location, items: AnyRef*): Unit =
    typing.report(error, location, items: _*)
}
