package onion.compiler.typing

import onion.compiler.*
import onion.compiler.SemanticError.*
import onion.compiler.TypedAST.*

private[typing] object MemberAccess {
  private def hasSamePackage(a: ClassType, b: ClassType): Boolean = {
    var name1 = a.name
    var name2 = b.name
    var index: Int = 0
    index = name1.lastIndexOf(".")
    if (index >= 0) name1 = name1.substring(0, index)
    else name1 = ""
    index = name2.lastIndexOf(".")
    name2 = if (index >= 0) name2.substring(0, index) else ""
    name1 == name2
  }

  def isTypeAccessible(target: ClassType, context: ClassType): Boolean =
    if (hasSamePackage(target, context)) true else (target.modifier & AST.M_INTERNAL) == 0

  def isMemberAccessible(member: MemberRef, context: ClassType): Boolean = {
    val targetType = member.affiliation
    val modifier = member.modifier
    if (targetType == context) {
      true
    } else if (TypeRules.isSuperType(targetType, context)) {
      (modifier & AST.M_PROTECTED) != 0 || (modifier & AST.M_PUBLIC) != 0
    } else {
      (AST.M_PUBLIC & modifier) != 0
    }
  }

  /**
   * [[isMemberAccessible]] for a member reached through `receiver`, the static type of
   * the expression before the dot. A protected instance member that is reachable only
   * because `context` subclasses its owner (another package) also needs the receiver
   * to be `context` or a subclass of it: Java's rule (JLS 6.6.2.1), which the JVM
   * verifier enforces. `new Object().clone()` passed the subclass check alone, then
   * failed to load with VerifyError "Bad access to protected data" (#2900).
   */
  def isMemberAccessible(member: MemberRef, context: ClassType, receiver: Type): Boolean =
    isMemberAccessible(member, context) && protectedReceiverAllowed(member, context, receiver)

  private def protectedReceiverAllowed(member: MemberRef, context: ClassType, receiver: Type): Boolean = {
    val modifier = member.modifier
    val owner = member.affiliation
    if ((modifier & AST.M_PUBLIC) != 0 || (modifier & AST.M_PROTECTED) == 0 || (modifier & AST.M_STATIC) != 0) true
    else if (owner == null || context == null || owner == context || hasSamePackage(owner, context)) true
    else erasedClass(receiver) match {
      case Some(cls) => isSubclassOf(cls, context)
      case None => true
    }
  }

  private def erasedClass(t: Type): Option[ClassType] = t match {
    case null => None
    case n: NullableType => erasedClass(n.innerType)
    case tv: TypeVariableType => erasedClass(tv.upperBound)
    case a: AppliedClassType => Some(a.raw)
    case c: ClassType => Some(c)
    case _ => None
  }

  private def isSubclassOf(cls: ClassType, context: ClassType): Boolean = {
    var current: ClassType = cls
    var steps = 0
    while (current != null && steps < 1000) {
      if (current == context || current.name == context.name) return true
      current = current.superClass
      steps += 1
    }
    false
  }

  def findField(target: ObjectType, name: String): FieldRef =
    if (target == null) {
      null
    } else {
      val direct = target.field(name)
      if (direct != null) {
        direct
      } else {
        val fromSuper = findField(target.superClass, name)
        if (fromSuper != null) {
          fromSuper
        } else {
          target.interfaces.iterator
            .map(findField(_, name))
            .find(_ != null)
            .getOrElse(null)
        }
      }
    }

  def ensureTypeAccessible(typing: Typing, node: AST.Node, target: ObjectType, context: ClassType): Boolean = {
    if (target.isArrayType) {
      val component = target.asInstanceOf[ArrayType].component
      // The component may be nullable (e.g. String?[]) or itself an array; only a plain
      // ClassType carries accessibility to check. Unwrap a NullableType; skip anything else
      // (previously this cast component straight to ClassType and crashed on String?[]).
      val componentClass = component match {
        case nt: NullableType => nt.innerType
        case other => other
      }
      componentClass match {
        case ct: ClassType =>
          if (!isTypeAccessible(ct, context)) {
            typing.report(CLASS_NOT_ACCESSIBLE, node, target, context)
            return false
          }
        case _ => ()
      }
    } else {
      target match {
        case classTarget: ClassType if isTypeAccessible(classTarget, context) => ()
        case _: ClassType =>
          typing.report(CLASS_NOT_ACCESSIBLE, node, target, context)
          return false
        case _ =>
          // Non-class, non-array targets (e.g., wildcard types) are not valid
          // accessibility targets; skip the accessibility check rather than crash.
          ()
      }
    }
    true
  }
}
