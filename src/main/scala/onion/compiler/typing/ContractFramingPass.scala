package onion.compiler.typing

import onion.compiler.{Modifier, SemanticError, Typing}
import onion.compiler.TypedAST.*
import onion.compiler.TermWalk

/**
 * Pass 8 of typing (RFC #2557, design 3.2, rule 1): an `invariant` or `history` may name only
 * fields whose type is a value type: a primitive, `String`, a boxed number, an enum, or a record.
 * A field that holds a mutable object can change without the owner's code running, so a clause
 * about it cannot be checked one call at a time.
 */
final class ContractFramingPass(typing: Typing) {
  private val valueClasses = Set("java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
    "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Boolean")

  def run(classes: Seq[ClassDefinition]): Unit =
    for (cd <- classes; clause <- cd.invariants ++ cd.histories)
      TermWalk.exists(clause) {
        case r: RefField =>
          if (!isValueType(r.field.`type`))
            typing.report(SemanticError.CONTRACT_FIELD_NOT_VALUE, r.location, r.field.name, r.field.`type`.name)
          false
        case _ => false
      }

  private def isValueType(t: Type): Boolean = t match {
    case _: BasicType => true
    case c: ClassDefinition => c.isRecord || Modifier.isEnum(c.modifier)
    case c: ClassType => valueClasses.contains(c.name) || Modifier.isEnum(c.modifier)
    case _ => false
  }
}
