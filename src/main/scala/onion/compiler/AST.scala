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
  def hasModifier(bitFlags: Int, modifier: Int): Boolean = (bitFlags & modifier) != 0
  def append[A](buffer: scala.collection.mutable.Buffer[A], element: A) { buffer += element }
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
  case class TypeNode(location: Location, desc: TypeDescriptor) extends Node
  abstract sealed class Node{ def location: Location }
  case class Argument(location: Location, name: String, typeRef: TypeNode) extends Node
  case class CompilationUnit(
    location: Location, sourceFile: String/*nullable*/, module: ModuleDeclaration/*nullable*/,
    imports: ImportClause, toplevels: List[Toplevel]) extends Node
  case class ModuleDeclaration(location: Location, name: String) extends Node
  case class ImportClause(location: Location, mapping: List[(String, String)]) extends Node
  abstract sealed class Toplevel extends Node
  abstract sealed class Expression extends Node
  abstract sealed class BinaryExpression(val symbol: String) extends Expression {
    def left: Expression
    def right: Expression
  }
  abstract sealed class UnaryExpression(val symbol: String) extends Expression {
    def target: Expression
  }
  case class Addition(location: Location, left: Expression, right: Expression) extends BinaryExpression("+")
  case class AdditionAssignment(location: Location, left: Expression, right: Expression) extends BinaryExpression("+=")
  case class Assignment(location: Location, left: Expression, right: Expression) extends BinaryExpression("=")
  case class BitAnd(location: Location, left: Expression, right: Expression) extends BinaryExpression("&")
  case class BitOr(location: Location, left: Expression, right: Expression) extends BinaryExpression("|")
  case class BooleanLiteral(location: Location, value: Boolean) extends Expression
  case class Cast(location: Location, src: Expression, to: TypeNode) extends Expression
  case class CharacterLiteral(location: Location, value: Char) extends Expression
  case class ClosureExpression(location: Location, typeRef: TypeNode, mname: String, args: List[Argument], returns: TypeNode/*nullable*/, body: BlockStatement) extends Expression
  case class CurrentInstance(location: Location) extends Expression
  case class Division(location: Location, left: Expression, right: Expression) extends BinaryExpression("/")
  case class DivisionAssignment(location: Location, left: Expression, right: Expression) extends BinaryExpression("/=")
  case class DoubleLiteral(location: Location, value: Double) extends Expression
  case class Elvis(location: Location, left: Expression, right: Expression) extends BinaryExpression(":?")
  case class Equal(location: Location, left: Expression, right: Expression) extends BinaryExpression("==")
  case class FloatLiteral(location: Location, value: Float) extends Expression
  case class GreaterOrEqual(location: Location, left: Expression, right: Expression) extends BinaryExpression(">=")
  case class GreaterThan(location: Location, left: Expression, right: Expression) extends BinaryExpression(">")
  case class Id(location: Location, name: String) extends Expression
  case class Indexing(location: Location, left: Expression, right: Expression) extends BinaryExpression("[]")
  case class IntegerLiteral(location: Location, value: Int) extends Expression
  case class IsInstance(location: Location, target: Expression, typeRef: TypeNode) extends Expression
  case class LessOrEqual(location: Location, left: Expression, right: Expression) extends BinaryExpression("<=")
  case class LessThan(location: Location, left: Expression, right: Expression) extends BinaryExpression("<")  
  case class ListLiteral(location: Location, elements: List[Expression]) extends Expression
  case class LogicalAnd(location: Location, left: Expression, right: Expression) extends BinaryExpression("&&")
  case class LogicalOr(location: Location, left: Expression, right: Expression) extends BinaryExpression("||")
  case class LogicalRightShift(location: Location, left: Expression, right: Expression) extends BinaryExpression(">>>")
  case class LongLiteral(location: Location, value: Long) extends Expression
  case class MathLeftShift(location: Location, left: Expression, right: Expression) extends BinaryExpression("<<")
  case class MathRightShift(location: Location, left: Expression, right: Expression) extends BinaryExpression(">>")
  case class MemberSelection(location: Location, target: Expression/*nullable*/, name: String) extends Expression
  case class MethodCall(location: Location, target: Expression/*nullable*/, name: String, args: List[Expression]) extends Expression
  case class Modulo(location: Location, left: Expression, right: Expression) extends BinaryExpression("%")
  case class ModuloAssignment(location: Location, left: Expression, right: Expression) extends BinaryExpression("%=")
  case class Multiplication(location: Location, left: Expression, right: Expression) extends BinaryExpression("*")
  case class MultiplicationAssignment(location: Location, left: Expression, right: Expression) extends BinaryExpression("*=")
  case class Negate(location: Location, target: Expression) extends UnaryExpression("-")
  case class NewArray(location: Location, typeRef: TypeNode, args: List[Expression]) extends Expression
  case class NewObject(location: Location, typeRef: TypeNode, args: List[Expression]) extends Expression
  case class Not(location: Location, target: Expression) extends UnaryExpression("!")
  case class NotEqual(location: Location, left: Expression, right: Expression) extends BinaryExpression("!=")
  case class NullLiteral(location: Location) extends Expression
  case class Posit(location: Location, target: Expression) extends UnaryExpression("+")
  case class PostDecrement(location: Location, target: Expression) extends UnaryExpression("--")
  case class PostIncrement(location: Location, target: Expression) extends UnaryExpression("++")
  case class ReferenceEqual(location: Location, left: Expression, right: Expression) extends BinaryExpression("===")
  case class ReferenceNotEqual(location: Location, left: Expression, right: Expression) extends BinaryExpression("!==")
  case class UnqualifiedFieldReference(location: Location, name: String) extends Expression
  case class UnqualifiedMethodCall(location: Location, name: String, args: List[Expression]) extends Expression
  case class StaticMemberSelection(location: Location, typeRef: TypeNode, name: String) extends Expression
  case class StaticMethodCall(location: Location, typeRef: TypeNode, name: String, args: List[Expression]) extends Expression
  case class StringLiteral(location: Location, value: String) extends Expression
  case class Subtraction(location: Location, left: Expression, right: Expression) extends BinaryExpression("-")
  case class SubtractionAssignment(location: Location, left: Expression, right: Expression) extends BinaryExpression("-=")
  case class SuperMethodCall(location: Location, name: String, args: List[Expression]) extends Expression
  case class XOR(location: Location, left: Expression, right: Expression) extends BinaryExpression("^")  
  
  abstract sealed class Statement extends Toplevel
  case class BlockStatement(location: Location, elements: List[Statement]) extends Statement
  case class BreakStatement(location: Location) extends Statement
  case class BranchStatement(location: Location, clauses: List[(Expression, BlockStatement)], elseBlock: BlockStatement/*nullable*/) extends Statement
  case class ContinueStatement(location: Location) extends Statement
  case class EmptyStatement(location: Location) extends Statement
  case class ExpressionStatement(location: Location, body: Expression) extends Statement
  case class ForeachStatement(location: Location, arg: Argument, collection: Expression, statement: BlockStatement) extends Statement
  case class ForStatement(location: Location, init: Statement, condition: Expression/*nullable*/, update: Expression/*nullable*/, block: BlockStatement) extends Statement
  case class IfStatement(location: Location, condition: Expression, thenBlock: BlockStatement, elseBlock: BlockStatement/*nullable*/) extends Statement
  case class LocalVariableDeclaration(location: Location, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends Statement
  case class ReturnStatement(location: Location, result: Expression/*nullable*/) extends Statement
  case class SelectStatement(location: Location, condition: Expression, cases: List[(List[Expression], BlockStatement)], elseBlock: BlockStatement/*nullable*/) extends Statement
  case class SynchronizedStatement(location: Location, condition: Expression, block: BlockStatement) extends Statement
  case class ThrowStatement(location: Location, target: Expression) extends Statement
  case class TryStatement(location: Location, tryBlock: BlockStatement, recClauses: List[(Argument, BlockStatement)], finBlock: BlockStatement/*nullable*/) extends Statement
  case class WhileStatement(location: Location, condition: Expression, block: BlockStatement) extends Statement
  
  case class FunctionDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockStatement) extends Toplevel
  case class GlobalVariableDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends Toplevel
  
  abstract sealed class MemberDeclaration extends Node { def modifiers: Int; def name: String } 
  case class MethodDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockStatement) extends MemberDeclaration
  case class FieldDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends MemberDeclaration
  case class DelegatedFieldDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression) extends MemberDeclaration
  case class ConstructorDeclaration(location: Location, modifiers: Int, args: List[Argument], superInits: List[Expression], block: BlockStatement) extends MemberDeclaration { val name = "new" }

  case class AccessSection(location: Location, modifiers: Int, members: List[MemberDeclaration]) extends Node
  abstract sealed class TypeDeclaration extends Toplevel { def modifiers: Int; def name: String }
  case class ClassDeclaration(location: Location, modifiers: Int, name: String, superClass: TypeNode, superInterfaces: List[TypeNode], defaultSection: Option[AccessSection], sections: List[AccessSection]) extends TypeDeclaration
  case class InterfaceDeclaration(location: Location, modifiers: Int, name: String, superInterfaces: List[TypeNode], methods: List[MethodDeclaration]) extends TypeDeclaration
}