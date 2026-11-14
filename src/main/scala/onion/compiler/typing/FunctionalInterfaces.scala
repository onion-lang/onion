package onion.compiler.typing

import onion.compiler.Modifier
import onion.compiler.TypedAST.{AppliedClassType, ClassType, Method}

/**
 * Where a lambda's target interface declares its single abstract method.
 *
 * Java's `UnaryOperator<T>` and `BinaryOperator<T>` declare no abstract method of their own:
 * their `apply` is inherited from `Function<T, T>` / `BiFunction<T, T, T>`. A lambda typed
 * against them must implement that inherited method, at the supertype's type arguments as
 * the target pins them (#2844).
 */
private[compiler] object FunctionalInterfaces {

  /** `equals`/`hashCode`/`toString` redeclared abstract do not count (Java's rule, e.g. `Comparator.equals`). */
  def isPublicObjectMethod(m: Method): Boolean = m.name match {
    case "equals" => m.arguments.length == 1 && m.arguments(0).name == "java.lang.Object"
    case "hashCode" | "toString" => m.arguments.isEmpty
    case _ => false
  }

  private def rawOf(ct: ClassType): ClassType = ct match {
    case a: AppliedClassType => a.raw
    case _ => ct
  }

  private def declaredAbstracts(ct: ClassType): Seq[Method] =
    rawOf(ct).methods.toSeq.filter(m => Modifier.isAbstract(m.modifier) && !isPublicObjectMethod(m))

  /**
   * The type whose declared methods hold `target`'s functional method: `target` itself when it
   * declares an abstract method, or else the one superinterface that does, applied at the type
   * arguments `target` gives it (`UnaryOperator[String]` -> `Function[String, String]`).
   * `target` unchanged when it is not an interface, or when no single inherited abstract method
   * name exists, so callers keep their own handling of those cases.
   */
  def functionalView(target: ClassType): ClassType = {
    if (target == null || !rawOf(target).isInterface || declaredAbstracts(target).nonEmpty) target
    else {
      val views = AppliedTypeViews.collectAppliedViewsFrom(target)
      val declaring = views.values.toSeq.filter(v => (rawOf(v) ne rawOf(target)) && declaredAbstracts(v).nonEmpty)
      val names = declaring.flatMap(declaredAbstracts).map(_.name).distinct
      // A default (or concrete) method of that name anywhere else in the hierarchy may
      // already implement it; then this is not a plain inherited SAM, so leave it alone.
      def implementedElsewhere(name: String): Boolean =
        (views.values.toSeq :+ target).exists { v =>
          rawOf(v).methods.exists(m => m.name == name && !Modifier.isAbstract(m.modifier) && !Modifier.isStatic(m.modifier))
        }
      declaring match {
        case Seq(only) if names.length == 1 && !implementedElsewhere(names.head) =>
          if (only.typeArguments.isEmpty) only.raw else only
        case _ => target
      }
    }
  }
}
