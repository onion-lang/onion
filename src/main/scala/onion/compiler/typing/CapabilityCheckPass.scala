package onion.compiler.typing

import onion.compiler.{Location, SemanticError, Typing}
import onion.compiler.toolbox.Message
import onion.compiler.TypedAST._
import onion.compiler.effects.{Effect, EffectInference}

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
final class CapabilityCheckPass(typing: Typing) {

  private val ToolMarker = "onion.tool"
  private val RequiresPrefix = "requires:"
  private val CapabilityForm = """([A-Za-z]+)(?:\(([A-Za-z0-9_]+)\))?""".r

  def run(classes: Seq[ClassDefinition]): Unit = {
    val tools: Seq[MethodDefinition] = for {
      cd <- classes
      m  <- cd.methods.toSeq
      md <- Some(m).collect { case md: MethodDefinition if md.annotations.contains(ToolMarker) => md }
    } yield md
    if (tools.isEmpty) return

    val (_, unitEffects) = EffectInference.inferUnits(classes)

    for (tool <- tools) {
      val declared = declaredCapabilities(tool)
      if (tool.getBlock != null) {
        checkUndeclared(tool, declared, classes, unitEffects)
        checkUnused(tool, declared, unitEffects)
      }
    }
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
