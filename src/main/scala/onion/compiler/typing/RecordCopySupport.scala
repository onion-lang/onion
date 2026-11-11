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
 *
 * The result is `None` when the call is not record-copy sugar (the caller
 * takes its regular path), `Some(None)` when it is and an error was reported,
 * and `Some(Some(term))` when it typed.
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
  )(buildRawCall: (Method, Array[Term]) => Term): Option[Option[Term]] = {
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
    boundary[Option[Option[Term]]] {
      val components = definition.recordComponents.getOrElse(break(None))
      // A record that declares its own `copy` gets no generated one (TypingOutlinePass).
      // A call that does not fit the components is then an ordinary call to the
      // declared overloads, so it falls back to the regular path instead of
      // reporting here: `copy(name = "s")` with `def copy(name: String)` (#2705).
      val declaresCopy = !definition.methods("copy").exists(m => (m.modifier & Modifier.SYNTHETIC_RECORD) != 0)

      val named = scala.collection.mutable.LinkedHashMap[String, AST.Expression]()
      args.foreach {
        case AST.NamedArgument(_, argName, value) => named(argName) = value
        case _ => break(None) // positional args (or a mix): use the regular path
      }
      for (argName <- named.keys if !components.exists(_._1 == argName)) {
        if (declaresCopy) break(None)
        calls.reportMethodNotFound(node, targetType, s"copy(${argName} = ...)", Array[Type]())
        break(Some(None)) // reported; the regular path would add a second error
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
                // The argument must fit the component as an assignment would:
                // `copy(b = "s")` on `b: Long?` is a type error, not a cast that
                // throws when the copy runs (#2705). This also boxes a primitive
                // for a reference component, widening first (`copy(b = 5)` on
                // `b: Long?` stores a Long).
                if (declaresCopy) {
                  // The record's own `copy` is an ordinary method: its overload is
                  // chosen by each argument's own type, never by the component's
                  // (`copy(a = 5)` with `copy(Int)` and `copy(Long)` on `a: Long`
                  // calls `copy(Int)`). Only the generated copy checks components.
                  val boxed =
                    if (!expectedType.isBasicType && term.isBasicType) onion.compiler.toolbox.Boxing.boxForSlot(calls.typing.table_, term, expectedType)
                    else term
                  fullParams(i) = expectedType match {
                    case _: NullableType if !boxed.`type`.isNullable && !boxed.`type`.isNullType =>
                      TypeSubst.withCast(boxed, expectedType)
                    case _ => boxed
                  }
                } else {
                  val assigned = calls.processAssignable(expr, expectedType, term)
                  if (assigned == null) break(Some(None))
                  // A nullable component (`port: Int?`) is declared `T?` in the raw
                  // signature; a non-null argument must be viewed as `T?` to match it
                  // (copy(port = 8080) -- #1993). null and `T?` terms already match.
                  fullParams(i) = expectedType match {
                    case _: NullableType if !assigned.`type`.isNullable && !assigned.`type`.isNullType =>
                      TypeSubst.withCast(assigned, expectedType)
                    case _ => assigned
                  }
                }
              case None => break(Some(None)) // the argument's own error is reported
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
          ) match {
            case built @ Some(_) => Some(built)
            case None => None
          }
        case _ => None
      }
    }
  }
}
