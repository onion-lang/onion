package onion.compiler.typing

import onion.compiler.{Modifier, SemanticError, Typing}
import onion.compiler.TypedAST.*
import onion.compiler.TermWalk

/**
 * Pass 8 of typing (RFC #2557, design 3.2, rule 1): an `invariant` or `history` may name only
 * fields whose type is a value type: a primitive, `String`, a boxed number, an enum, or a record.
 * A field that holds a mutable object can change without the owner's code running, so a clause
 * about it cannot be checked one call at a time.
 *
 * Rule 2 (#2599): such a field is written only through `this`. A write through any other
 * reference (`o.t = 0`, `cs[0].t = 0`, a factory's `val c = new C(); c.t = 0`) is E0110, wherever
 * it is made, since no check of one call at a time can follow it. The typed tree has already
 * resolved which field a write targets and whether its receiver is `this`, so nothing here
 * depends on how a name is declared.
 */
final class ContractFramingPass(typing: Typing) {
  private val valueClasses = Set("java.lang.String", "java.lang.Integer", "java.lang.Long", "java.lang.Double",
    "java.lang.Float", "java.lang.Short", "java.lang.Byte", "java.lang.Character", "java.lang.Boolean")

  def run(classes: Seq[ClassDefinition]): Unit = {
    // Rule 4: a Java superclass can have mutators the compiler can neither prove nor wrap.
    for (cd <- classes if cd.invariants.nonEmpty || cd.histories.nonEmpty) {
      val sup = cd.superClass
      if (sup != null && sup.name != "java.lang.Object" && !sup.isInstanceOf[ClassDefinition] && !cd.isRecord &&
          !Modifier.isEnum(cd.modifier))
        typing.report(SemanticError.CONTRACT_JAVA_SUPERCLASS, cd.location, cd.name, sup.name)
    }
    checkFields(classes)
    checkWrites(classes)
  }

  /** `(declaring class, field)` for every field an `invariant` or `history` names. */
  private def namedFields(classes: Seq[ClassDefinition]): Set[(String, String)] = {
    val named = Set.newBuilder[(String, String)]
    for (cd <- classes; clause <- cd.invariants ++ cd.histories)
      TermWalk.exists(clause, intoClosures = true) {
        case r: RefField if r.field.affiliation != null => named += ((r.field.affiliation.name, r.field.name)); false
        case _ => false
      }
    named.result()
  }

  private def checkWrites(classes: Seq[ClassDefinition]): Unit = {
    val named = namedFields(classes)
    if (named.nonEmpty) {
      def isThis(t: Term): Boolean = t.isInstanceOf[This] || t.isInstanceOf[OuterThis]
      def scan(body: ActionStatement): Unit = if (body != null) {
        // `t++` and `this.t++` are lowered to `tmp = this; tmp.t = tmp.t + 1`: that write is through `this`.
        val viaThisTemp = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap[SetField, java.lang.Boolean])
        TermWalk.existsIn(body, intoClosures = true) {
          case b: Begin if b.terms.length >= 2 =>
            (b.terms(0), b.terms(1)) match {
              case (s: SetLocal, w: SetField) if s.frame == 0 && isThis(s.value) =>
                w.target match {
                  case r: RefLocal if r.frame == 0 && r.index == s.index => viaThisTemp.add(w)
                  case _ =>
                }
              case _ =>
            }
            false
          case w: SetField if w.field.affiliation != null && named.contains((w.field.affiliation.name, w.field.name)) &&
              !isThis(w.target) && !viaThisTemp.contains(w) =>
            typing.report(SemanticError.CONTRACT_FIELD_NOT_VALUE, w.location, w.field.name, w.field.affiliation.name, "write")
            false
          case _ => false
        }
      }
      for (cd <- classes) {
        cd.methods.foreach { case m: MethodDefinition => scan(m.block); case _ => () }
        cd.constructors.foreach { case c: ConstructorDefinition => scan(c.block); case _ => () }
        cd.staticInitializers.foreach(scan)
      }
    }
  }

  private def checkFields(classes: Seq[ClassDefinition]): Unit =
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
