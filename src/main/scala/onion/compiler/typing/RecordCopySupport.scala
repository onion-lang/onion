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
  )(buildCallOn: (Term, Method, Array[Term]) => Term): Option[Option[Term]] = {
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
        case na @ AST.NamedArgument(_, argName, value) =>
          // A name given twice is E0044, as for an ordinary call; the first one would never run (#3100).
          if (named.contains(argName)) {
            calls.reportDuplicateArgument(na, argName)
            break(Some(None))
          }
          named(argName) = value
        case _ => break(None) // positional args (or a mix): use the regular path
      }
      // This path fills the call by component position, so it serves only an own `copy`
      // overload that takes one argument per component and names no component in another
      // position: `copy(a, b)` and `copy(nx, ny)` qualify, the reordered `copy(b, a)` does
      // not. Overloads of another arity cannot take this call and do not matter. With no
      // qualifying overload, or when the one chosen by type does not qualify, the call is an
      // ordinary named-argument call, bound by parameter name (#3100).
      val componentNames = components.map(_._1)
      def takesComponentsInOrder(m: Method): Boolean = {
        val names = m.argumentsWithDefaults.map(_.name)
        names.length == componentNames.length && names.indices.forall(i =>
          names(i) == componentNames(i) || !componentNames.contains(names(i)))
      }
      if (declaresCopy && !definition.methods("copy").exists(takesComponentsInOrder)) break(None)
      for (argName <- named.keys if !components.exists(_._1 == argName)) {
        if (declaresCopy) break(None)
        calls.reportMethodNotFound(node, targetType, s"copy(${argName} = ...)", Array[Type]())
        break(Some(None)) // reported; the regular path would add a second error
      }

      // The receiver runs once, however many components the copy keeps: an omitted
      // component is read from a temporary, not by running `mk()` again (#3098).
      // `this` and a local are read in place, which keeps the call free of temporaries.
      val omitsComponent = components.exists(c => !named.contains(c._1))
      val (receiver, receiverStore): (Term, Term) = target match {
        case _: This | _: OuterThis | _: RefLocal => (target, null)
        case _ if !omitsComponent || context == null => (target, null)
        case _ =>
          val tp = target.`type`
          val bind = new ClosureLocalBinding(0, context.add(context.newName, tp, isMutable = false), tp, isMutable = false)
          (new RefLocal(bind), new SetLocal(bind, target))
      }
      def buildRawCall(method: Method, finalParams: Array[Term]): Term = {
        val call = buildCallOn(receiver, method, finalParams)
        if (receiverStore == null) call else new Begin(receiverStore, call)
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
                val call = new Call(receiver, getter, Array[Term]())
                fullParams(i) = TypeSubst.withCast(call, TypeSubst.withClassOnly(getter.returnType, targetType))
              case _ => break(None)
            }
        }
        i += 1
      }

      // `copy(b = f(), a = g())` runs `f()` first, as every other named call does (#2801).
      val orderedParams = ArgumentHelpers.evaluateInWrittenOrder(args, components.map(_._1), fullParams, context)
      targetType.findMethod("copy", orderedParams) match {
        case Array(method, _*) if declaresCopy && !takesComponentsInOrder(method) => None
        case Array(method, _*) =>
          calls.buildResolvedCall(node, method, orderedParams, typeArgs, classSubst, null, context)(
            expectedArgs => calls.processParamsWithExpected(node, orderedParams, expectedArgs),
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
