package onion.compiler.typing

import onion.compiler.TypedAST.*

/** How a type-class instance's type argument is named when instances are compared: the
  * fully qualified, boxed class name (`java.lang.Integer` for `Int`, `pa.Point` for a record
  * in `module pa`). Both the lookup of `Trait[T]` and the E0097 coherence check use it, so
  * two types that share a simple name never share an instance (#2770). */
private[compiler] object TraitInstanceKeys {
  def typeArgumentName(tp: Type): String = tp match {
    case b: BasicType => b.name match {
      case "int" => "java.lang.Integer"; case "long" => "java.lang.Long"
      case "double" => "java.lang.Double"; case "float" => "java.lang.Float"
      case "boolean" => "java.lang.Boolean"; case "byte" => "java.lang.Byte"
      case "short" => "java.lang.Short"; case "char" => "java.lang.Character"
      case other => other
    }
    case a: AppliedClassType => a.raw.name
    case other => other.name
  }
}
