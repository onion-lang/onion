package onion.compiler.toolbox

import onion.compiler.TypedAST
import onion.compiler.TypedAST.*

/**
 * Renders a type the way it is written in Onion source (e.g. `Map[String, Int]`
 * rather than the JVM form `java.util.Map[java.lang.String, java.lang.Integer]`).
 * Shared by the REPL (`:type`, result display) and the diagnostic reporter so
 * user-facing type names are consistent. `TypedAST.Type#displayName` remains the
 * JVM-oriented form used internally and in codegen.
 */
object TypeFormatting {

  private val boxedToPrimitive: Map[String, String] = Map(
    "java.lang.Integer" -> "Int", "java.lang.Long" -> "Long",
    "java.lang.Double" -> "Double", "java.lang.Float" -> "Float",
    "java.lang.Boolean" -> "Boolean", "java.lang.Byte" -> "Byte",
    "java.lang.Short" -> "Short", "java.lang.Character" -> "Char"
  )

  /** A fully-qualified class name in source form: strip the package, turn nested
    * `$` into `.`, and map boxed wrappers to their Onion primitive names. */
  private def sourceClassName(fqcn: String): String =
    boxedToPrimitive.getOrElse(fqcn, {
      val dot = fqcn.lastIndexOf('.')
      val simple = if (dot >= 0) fqcn.substring(dot + 1) else fqcn
      simple.replace('$', '.')
    })

  def sourceForm(tp: TypedAST.Type): String = tp match {
    case null => "<unknown>"
    case n: NullableType => sourceForm(n.innerType) + "?"
    case a: AppliedClassType =>
      val base = sourceClassName(a.raw.name)
      if (a.typeArguments.isEmpty) base
      else base + "[" + a.typeArguments.map(sourceForm).mkString(", ") + "]"
    case arr: ArrayType => sourceForm(arr.component) + ("[]" * math.max(1, arr.dimension))
    case w: WildcardType =>
      w.lowerBound match {
        case Some(lo) => "? super " + sourceForm(lo)
        case None if w.upperBound.name == "java.lang.Object" => "?"
        case None => "? extends " + sourceForm(w.upperBound)
      }
    case bt: BasicType => bt.displayName
    case tv: TypeVariableType => tv.name
    case ct: ClassType => sourceClassName(ct.name)
    case other => sourceClassName(other.name)
  }
}
