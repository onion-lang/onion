package onion.compiler.typing

import onion.compiler.*
import onion.compiler.TypedAST.*

import scala.util.boundary
import scala.util.boundary.break

/**
 * Record `copy` sugar (`p.copy()` full clone, `p.copy(y = 9)` partial copy
 * with named arguments) shared between the plain member-call path
 * (`InstanceMethodCallSupport`) and the safe-navigation path
 * (`SafeNavigationTypingSupport`), which differ only in how the resolved
 * `copy` method is wrapped into a call term (`Call` vs `SafeCall`).
 */
private[compiler] final class RecordCopySupport(calls: MethodCallTyping) {

  def tryRecordCopy(
    node: AST.Node,
    name: String,
    args: List[AST.Expression],
    typeArgs: List[AST.TypeNode],
    context: LocalContext,
    target: Term,
    targetType: ObjectType
  )(buildRawCall: (Method, Array[Term]) => Term): Option[Term] = {
    if (name != "copy") return None
    val definition = targetType match {
      case d: ClassDefinition => d
      case applied: AppliedClassType =>
        applied.raw match {
          case d: ClassDefinition => d
          case _ => return None
        }
      case _ => return None
    }
    boundary[Option[Term]] {
      val components = definition.recordComponents.getOrElse(break(None))

      val named = scala.collection.mutable.LinkedHashMap[String, AST.Expression]()
      args.foreach {
        case AST.NamedArgument(_, argName, value) => named(argName) = value
        case _ => break(None) // positional args (or a mix): use the regular path
      }
      for (argName <- named.keys if !components.exists(_._1 == argName)) {
        calls.reportMethodNotFound(node, targetType, s"copy(${argName} = ...)", Array[Type]())
        break(None) // reported; abort typing
      }

      val classSubst = TypeSubstitution.classSubstitution(targetType)
      val fullParams = new Array[Term](components.length)
      var i = 0
      while (i < components.length) {
        val (cname, ctype) = components(i)
        val expectedType = TypeSubstitution.substituteType(ctype, classSubst, scala.collection.immutable.Map.empty, defaultToBound = true)
        named.get(cname) match {
          case Some(expr) =>
            calls.typed(expr, context, expectedType) match {
              case Some(term) =>
                // Box primitives against reference-typed components so the
                // raw signature (copy(A, B)) still matches: copy(second = 42)
                fullParams(i) =
                  if (!expectedType.isBasicType && term.isBasicType) onion.compiler.toolbox.Boxing.boxing(calls.typing.table_, term)
                  else term
              case None => break(None)
            }
          case None =>
            targetType.findMethod(cname, Array[Term]()) match {
              case Array(getter, _*) =>
                // Specialize the component type for applied records so the
                // kept value of first() on Pair[String, Integer] is a String.
                // The receiver here is already known non-null by the point the
                // (possibly safe-nav) outer call is built, so a plain getter
                // call is correct for both the plain and safe-nav callers.
                val call = new Call(target, getter, Array[Term]())
                fullParams(i) = TypeSubst.withCast(call, TypeSubst.withClassOnly(getter.returnType, targetType))
              case _ => break(None)
            }
        }
        i += 1
      }

      targetType.findMethod("copy", fullParams) match {
        case Array(method, _*) =>
          calls.buildResolvedCall(node, method, fullParams, typeArgs, classSubst, null)(
            expectedArgs => calls.processParamsWithExpected(node, fullParams, expectedArgs),
            finalParams => buildRawCall(method, finalParams)
          )
        case _ => None
      }
    }
  }
}
