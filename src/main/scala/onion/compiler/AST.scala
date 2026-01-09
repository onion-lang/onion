package onion.compiler

object AST {
  val NIL: Any = Nil
  val M_INTERNAL = 1
  val M_SYNCHRONIZED = 2
  val M_FINAL = 4
  val M_ABSTRACT = 8
  val M_VOLATILE = 16
  val M_STATIC = 32
  val M_OVERRIDE = 64
  val M_PUBLIC = 128
  val M_PROTECTED = 256
  val M_PRIVATE = 512
  val M_FORWARDED = 1024
  val M_SEALED = 2048
  def hasModifier(bitFlags: Int, modifier: Int): Boolean = (bitFlags & modifier) != 0
  def append[A](buffer: scala.collection.mutable.Buffer[A], element: A): Unit = {
    buffer += element
  }
  abstract sealed class TypeDescriptor
  case class PrimitiveType(kind: PrimitiveTypeKind) extends TypeDescriptor {
    override def toString: String = kind.toString
  }
  case class ReferenceType(name: String, qualified: Boolean) extends TypeDescriptor {
    override def toString: String = name
  }
  case class ParameterizedType(component: TypeDescriptor, params: List[TypeDescriptor]) extends TypeDescriptor {
    override def toString: String = component.toString + params.map(_.toString).mkString("[", ",", "]")
  }
  case class FunctionType(params: List[TypeDescriptor], result: TypeDescriptor) extends TypeDescriptor {
    override def toString: String = params.map(_.toString).mkString("(", ", ", ")") + " -> " + result.toString
  }
  case class ArrayType(component: TypeDescriptor) extends TypeDescriptor {
    override def toString: String = s"${component.toString}[]"
  }
  abstract sealed class PrimitiveTypeKind(val name: String) {
    override def toString: String = name
  }
  case object KByte extends PrimitiveTypeKind("byte")
  case object KShort extends PrimitiveTypeKind("short")
  case object KInt extends PrimitiveTypeKind("int")
  case object KLong extends PrimitiveTypeKind("long")
  case object KChar extends PrimitiveTypeKind("char")
  case object KFloat extends PrimitiveTypeKind("float")
  case object KDouble extends PrimitiveTypeKind("double")
  case object KBoolean extends PrimitiveTypeKind("boolean")
  case object KVoid extends PrimitiveTypeKind("void")
  //workaround to be used from Java
  val K_BYTE = KByte
  val K_SHORT = KShort
  val K_INT = KInt
  val K_LONG = KLong
  val K_CHAR = KChar
  val K_FLOAT = KFloat
  val K_DOUBLE = KDouble
  val K_BOOLEAN = KBoolean
  val K_VOID = KVoid
  case class TypeNode(location: Location, desc: TypeDescriptor, isRelaxed: Boolean) extends Node
  abstract sealed class Node{ def location: Location }
  case class Argument(location: Location, name: String, typeRef: TypeNode, defaultValue: Expression = null) extends Node
  case class CompilationUnit(
    location: Location, sourceFile: String/*nullable*/, module: ModuleDeclaration/*nullable*/,
    imports: ImportClause, toplevels: List[Toplevel]) extends Node
  case class ModuleDeclaration(location: Location, name: String) extends Node
  case class ImportClause(location: Location, mapping: List[(String, String)]) extends Node
  abstract sealed class Toplevel extends Node
  abstract sealed class Expression extends Toplevel
  abstract sealed class BinaryExpression(val symbol: String) extends Expression {
    def lhs: Expression
    def rhs: Expression
  }
  abstract sealed class UnaryExpression(val symbol: String) extends Expression {
    def term: Expression
  }
  case class Addition(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("+")
  case class AdditionAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("+=")
  case class Assignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("=")
  case class BitAnd(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("&")
  case class BitAndAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("&=")
  case class BitOr(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("|")
  case class BitOrAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("|=")
  case class BooleanLiteral(location: Location, value: Boolean) extends Expression
  case class ByteLiteral(location: Location, value: Byte) extends Expression
  case class Cast(location: Location, src: Expression, to: TypeNode) extends Expression
  case class CharacterLiteral(location: Location, value: Char) extends Expression
  case class ClosureExpression(location: Location, typeRef: TypeNode, mname: String, args: List[Argument], returns: TypeNode/*nullable*/, body: BlockExpression) extends Expression
  case class CurrentInstance(location: Location) extends Expression
  case class Division(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("/")
  case class DivisionAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("/=")
  case class DoubleLiteral(location: Location, value: Double) extends Expression
  case class Elvis(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(":?")
  case class Equal(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("==")
  case class FloatLiteral(location: Location, value: Float) extends Expression
  case class GreaterOrEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">=")
  case class GreaterThan(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">")
  case class Id(location: Location, name: String) extends Expression
  case class Indexing(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("[]")
  case class IntegerLiteral(location: Location, value: Int) extends Expression
  case class IsInstance(location: Location, target: Expression, typeRef: TypeNode) extends Expression
  case class LessOrEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<=")
  case class LeftShiftAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<<=")
  case class LessThan(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<")
  case class ListLiteral(location: Location, elements: List[Expression]) extends Expression
  case class LogicalAnd(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("&&")
  case class LogicalOr(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("||")
  case class LogicalRightShift(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">>>")
  case class LogicalRightShiftAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">>>=")
  case class LongLiteral(location: Location, value: Long) extends Expression
  case class ShortLiteral(location: Location, value: Short) extends Expression
  case class MathLeftShift(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<<")
  case class MathRightShift(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">>")
  case class MathRightShiftAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">>=")
  case class MemberSelection(location: Location, target: Expression/*nullable*/, name: String) extends Expression
  case class MethodCall(location: Location, target: Expression/*nullable*/, name: String, args: List[Expression], typeArgs: List[TypeNode] = Nil) extends Expression {
    def this(location: Location, target: Expression, name: String, args: List[Expression]) =
      this(location, target, name, args, Nil)
  }
  case class Modulo(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("%")
  case class ModuloAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("%=")
  case class Multiplication(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("*")
  case class MultiplicationAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("*=")
  case class Negate(location: Location, term: Expression) extends UnaryExpression("-")
  case class NewArray(location: Location, typeRef: TypeNode, args: List[Expression]) extends Expression
  case class NewArrayWithValues(location: Location, typeRef: TypeNode, values: List[Expression]) extends Expression
  case class NewObject(location: Location, typeRef: TypeNode, args: List[Expression]) extends Expression
  case class Not(location: Location, term: Expression) extends UnaryExpression("!")
  case class NotEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("!=")
  case class NullLiteral(location: Location) extends Expression
  case class Posit(location: Location, term: Expression) extends UnaryExpression("+")
  case class PostDecrement(location: Location, term: Expression) extends UnaryExpression("--")
  case class PostIncrement(location: Location, term: Expression) extends UnaryExpression("++")
  case class ReferenceEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("===")
  case class ReferenceNotEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("!==")
  case class UnqualifiedFieldReference(location: Location, name: String) extends Expression
  case class UnqualifiedMethodCall(location: Location, name: String, args: List[Expression], typeArgs: List[TypeNode] = Nil) extends Expression {
    def this(location: Location, name: String, args: List[Expression]) =
      this(location, name, args, Nil)
  }
  case class NamedArgument(location: Location, name: String, value: Expression) extends Expression
  case class StaticMemberSelection(location: Location, typeRef: TypeNode, name: String) extends Expression
  case class StaticMethodCall(location: Location, typeRef: TypeNode, name: String, args: List[Expression], typeArgs: List[TypeNode] = Nil) extends Expression {
    def this(location: Location, typeRef: TypeNode, name: String, args: List[Expression]) =
      this(location, typeRef, name, args, Nil)
  }
  case class StringLiteral(location: Location, value: String) extends Expression
  case class StringInterpolation(location: Location, parts: List[String], expressions: List[Expression]) extends Expression
  case class Subtraction(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("-")
  case class SubtractionAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("-=")
  case class SuperMethodCall(location: Location, name: String, args: List[Expression], typeArgs: List[TypeNode] = Nil) extends Expression {
    def this(location: Location, name: String, args: List[Expression]) =
      this(location, name, args, Nil)
  }
  case class XOR(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("^")
  case class XorAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("^=")

  // Patterns for select/case
  abstract sealed class Pattern extends Node
  case class ExpressionPattern(expr: Expression) extends Pattern { def location: Location = expr.location }
  case class TypePattern(location: Location, name: String, typeRef: TypeNode) extends Pattern
  case class WildcardPattern(location: Location) extends Pattern
  case class DestructuringPattern(location: Location, constructor: String, bindings: List[Pattern]) extends Pattern
  // BindingPattern is a simple variable binding (used inside destructuring)
  case class BindingPattern(location: Location, name: String) extends Pattern
  case class GuardedPattern(location: Location, pattern: Pattern, guard: Expression) extends Pattern

  abstract sealed class CompoundExpression extends Expression
  case class BlockExpression(location: Location, elements: List[CompoundExpression]) extends CompoundExpression
  case class BreakExpression(location: Location) extends CompoundExpression
  case class ContinueExpression(location: Location) extends CompoundExpression
  case class EmptyExpression(location: Location) extends CompoundExpression
  case class ExpressionBox(location: Location, body: Expression) extends CompoundExpression
  case class ForeachExpression(location: Location, arg: Argument, collection: Expression, statement: BlockExpression) extends CompoundExpression
  case class ForExpression(location: Location, init: CompoundExpression, condition: Expression /*nullable*/ , update: Expression /*nullable*/ , block: BlockExpression) extends CompoundExpression
  case class IfExpression(location: Location, condition: Expression, thenBlock: BlockExpression, elseBlock: BlockExpression /*nullable*/) extends CompoundExpression
  case class LocalVariableDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode/*nullable*/, init: Expression/*nullable*/) extends CompoundExpression
  case class ReturnExpression(location: Location, result: Expression /*nullable*/) extends CompoundExpression
  case class SelectExpression(location: Location, condition: Expression, cases: List[(List[Pattern], BlockExpression)], elseBlock: BlockExpression /*nullable*/) extends CompoundExpression
  case class SynchronizedExpression(location: Location, condition: Expression, block: BlockExpression) extends CompoundExpression
  case class ThrowExpression(location: Location, target: Expression) extends CompoundExpression
  case class TryExpression(location: Location, resources: List[LocalVariableDeclaration], tryBlock: BlockExpression, recClauses: List[(Argument, BlockExpression)], finBlock: BlockExpression /*nullable*/) extends CompoundExpression
  case class WhileExpression(location: Location, condition: Expression, block: BlockExpression) extends CompoundExpression

  case class FunctionDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, throwsTypes: List[TypeNode] = Nil) extends Toplevel
  case class GlobalVariableDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends Toplevel

  abstract sealed class MemberDeclaration extends Node { def modifiers: Int; def name: String }
  case class TypeParameter(location: Location, name: String, upperBound: Option[TypeNode] = None) extends Node

  case class MethodDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, typeParameters: List[TypeParameter] = Nil, throwsTypes: List[TypeNode] = Nil) extends MemberDeclaration {
    def this(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression) =
      this(location, modifiers, name, args, returnType, block, Nil, Nil)
    def this(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, typeParameters: List[TypeParameter]) =
      this(location, modifiers, name, args, returnType, block, typeParameters, Nil)
  }
  case class FieldDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends MemberDeclaration
  case class DelegatedFieldDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression) extends MemberDeclaration
  case class ConstructorDeclaration(location: Location, modifiers: Int, args: List[Argument], superInits: List[Expression], block: BlockExpression) extends MemberDeclaration {val name = "new" }

  case class AccessSection(location: Location, modifiers: Int, members: List[MemberDeclaration]) extends Node
  abstract sealed class TypeDeclaration extends Toplevel { def modifiers: Int; def name: String }
  case class RecordDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], superInterfaces: List[TypeNode] = Nil) extends TypeDeclaration
  case class ClassDeclaration(location: Location, modifiers: Int, name: String, superClass: TypeNode, superInterfaces: List[TypeNode], defaultSection: Option[AccessSection], sections: List[AccessSection], typeParameters: List[TypeParameter] = Nil) extends TypeDeclaration {
    def this(location: Location, modifiers: Int, name: String, superClass: TypeNode, superInterfaces: List[TypeNode], defaultSection: Option[AccessSection], sections: List[AccessSection]) =
      this(location, modifiers, name, superClass, superInterfaces, defaultSection, sections, Nil)
  }
  case class InterfaceDeclaration(location: Location, modifiers: Int, name: String, superInterfaces: List[TypeNode], methods: List[MethodDeclaration], typeParameters: List[TypeParameter] = Nil) extends TypeDeclaration {
    def this(location: Location, modifiers: Int, name: String, superInterfaces: List[TypeNode], methods: List[MethodDeclaration]) =
      this(location, modifiers, name, superInterfaces, methods, Nil)
  }
}
