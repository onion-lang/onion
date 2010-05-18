package onion.compiler.syntax

object AST {
  abstract sealed class TypeDescriptor
  case class PrimitiveType(kind: PrimitiveTypeKind) extends TypeDescriptor
  case class ReferenceType(name: String, qualified: Boolean) extends TypeDescriptor
  case class ParameterizedType(component: TypeDescriptor, params: List[TypeDescriptor]) extends TypeDescriptor
  case class ArrayType(component: TypeDescriptor) extends TypeDescriptor
  abstract sealed class PrimitiveTypeKind
  case object KByte extends PrimitiveTypeKind
  case object KShort extends PrimitiveTypeKind
  case object KInt extends PrimitiveTypeKind
  case object KLong extends PrimitiveTypeKind
  case object KChar extends PrimitiveTypeKind
  case object KFloat extends PrimitiveTypeKind
  case object KDouble extends PrimitiveTypeKind
  case object KBoolean extends PrimitiveTypeKind
  case object KVoid extends PrimitiveTypeKind
  case class TypeNode(pos: Position, desc: TypeDescriptor) extends Node
  case class Position(line: Int, column: Int)
  abstract sealed class Node{ def pos: Position }
  case class Argument(pos: Position, name: String, typeRef: TypeNode) extends Node
  case class CompilationUnit(
    pos: Position, sourceFile: String = "<generated>", module: ModuleDeclaration,
    imports: ImportClause, toplevels: List[Toplevel]) extends Node
  case class ModuleDeclaration(pos: Position, name: String) extends Node
  case class ImportClause(pos: Position, mapping: List[(String, String)]) extends Node
  abstract sealed class Toplevel extends Node
  abstract sealed class Expression extends Node
  abstract sealed class BinaryExpression(symbol: String) extends Expression {
    def left: Expression
    def right: Expression
  }
  abstract sealed class UnaryExpression(symbol: String) extends Expression {
    def target: Expression
  }
  case class Addition(pos: Position, left: Expression, right: Expression) extends BinaryExpression("+")
  case class AdditionAssignment(pos: Position, left: Expression, right: Expression) extends BinaryExpression("+=")
  case class Assignment(pos: Position, left: Expression, right: Expression) extends BinaryExpression("=")
  case class BitAnd(pos: Position, left: Expression, right: Expression) extends BinaryExpression("&")
  case class BitOr(pos: Position, left: Expression, right: Expression) extends BinaryExpression("|")
  case class BooleanLiteral(pos: Position, value: Boolean) extends Expression
  case class Cast(pos: Position, src: Expression, to: TypeNode) extends Expression
  case class CharacterLiteral(pos: Position, value: Char) extends Expression
  case class ClosureExpression(pos: Position, typeRef: TypeNode, args: Argument, returns: TypeNode) extends Expression
  case class CurrentInstance(pos: Position) extends Expression
  case class Division(pos: Position, left: Expression, right: Expression) extends BinaryExpression("/")
  case class DivisionAssignment(pos: Position, left: Expression, right: Expression) extends BinaryExpression("/=")
  case class DoubleLiteral(pos: Position, value: Double) extends Expression
  case class Elvis(pos: Position, left: Expression, right: Expression) extends BinaryExpression(":?")
  case class Equal(pos: Position, left: Expression, right: Expression) extends BinaryExpression("==")
  case class FloatLiteral(pos: Position, value: Float) extends Expression
  case class GreaterOrEqual(pos: Position, left: Expression, right: Expression) extends BinaryExpression(">=")
  case class GreaterThan(pos: Position, left: Expression, right: Expression) extends BinaryExpression(">")
  case class Id(pos: Position, name: String) extends Expression
  case class Indexing(pos: Position, left: Expression, right: Expression) extends BinaryExpression("[]")
  case class IntegerLiteral(pos: Position, value: Int) extends Expression
  case class IsInstance(pos: Position, typeRef: TypeNode) extends Expression
  case class LessOrEqual(pos: Position, left: Expression, right: Expression) extends BinaryExpression("<=")
  case class LessThan(pos: Position, left: Expression, right: Expression) extends BinaryExpression("<")  
  case class ListLiteral(pos: Position, elements: List[Expression]) extends Expression
  case class LogicalAnd(pos: Position, left: Expression, right: Expression) extends BinaryExpression("&&")
  case class LogicalOr(pos: Position, left: Expression, right: Expression) extends BinaryExpression("||")
  case class LogicalRightShift(pos: Position, left: Expression, right: Expression) extends BinaryExpression(">>>")
  case class LongLiteral(pos: Position, value: Long) extends Expression
  case class MathLeftShift(pos: Position, left: Expression, right: Expression) extends BinaryExpression("<<")
  case class MathRightShift(pos: Position, left: Expression, right: Expression) extends BinaryExpression(">>")
  case class MemberSelection(pos: Position, target: Expression, name: String) extends Expression
  case class MethodCall(pos: Position, target: Expression, name: String, args: List[Expression]) extends Expression
  case class Modulo(pos: Position, left: Expression, right: Expression) extends BinaryExpression("%")
  case class ModuloAssignment(pos: Position, left: Expression, right: Expression) extends BinaryExpression("%=")
  case class Multiplication(pos: Position, left: Expression, right: Expression) extends BinaryExpression("*")
  case class MultiplicationAssignment(pos: Position, left: Expression, right: Expression) extends BinaryExpression("*=")
  case class Negate(pos: Position, target: Expression) extends UnaryExpression("-")
  case class NewArray(pos: Position, typeRef: TypeNode, args: List[Expression]) extends Expression
  case class NewObject(pos: Position, typeRef: TypeNode, args: List[Expression]) extends Expression
  case class Not(pos: Position, target: Expression) extends UnaryExpression("!")
  case class NotEqual(pos: Position, left: Expression, right: Expression) extends BinaryExpression("!=")
  case class NullLiteral(pos: Position) extends Expression
  case class Posit(pos: Position, target: Expression) extends UnaryExpression("+")
  case class PostDecrement(pos: Position, target: Expression) extends UnaryExpression("--")
  case class PostIncrement(pos: Position, target: Expression) extends UnaryExpression("++")
  case class ReferenceEqual(pos: Position, target: Expression) extends UnaryExpression("===")
  case class ReferenceNotEqual(pos: Position, target: Expression) extends UnaryExpression("!==")
  case class UnqualifiedFieldReference(pos: Position, name: String) extends Expression
  case class UnqualifiedMethodCall(pos: Position, name: String, args: Expression) extends Expression
  case class StaticIDExpression(pos: Position, typeRef: TypeNode, name: String) extends Expression
  case class StaticMethodCall(pos: Position, typeRef: TypeNode, name: String, args: List[Expression]) extends Expression
  case class StringLiteral(pos: Position, value: String) extends Expression
  case class Subtraction(pos: Position, left: Expression, right: Expression) extends BinaryExpression("-")
  case class SubtractionAssignment(pos: Position, left: Expression, right: Expression) extends BinaryExpression("-=")
  case class SuperMethodCall(pos: Position, name: String, args: List[Expression]) extends Expression
  case class XOR(pos: Position, left: Expression, right: Expression) extends BinaryExpression("^")  
  
  abstract sealed class Statement extends Toplevel
  case class BlockStatement(pos: Position, elements: List[Statement]) extends Statement
  case class BreakStatement(pos: Position) extends Statement
  case class CondStatement(pos: Position, clauses: List[(Expression, BlockStatement)], elseBlock: Option[BlockStatement]) extends Statement
  case class ContinueStatement(pos: Position) extends Statement
  case class EmptyStatement(pos: Position) extends Statement
  case class ForeachStatement(pos: Position, arg: Argument, collection: Expression, statement: BlockStatement) extends Statement
  case class ForStatement(pos: Position, init: Statement, condition: Expression, update: Expression, block: BlockStatement) extends Statement
  case class IfStatement(pos: Position, condition: Expression, thenBlock: BlockStatement, elseBlock: BlockStatement) extends Statement
  case class LocalVariableDeclaration(pos: Position, name: String, typeRef: TypeNode, init: Expression) extends Statement
  case class ReturnStatement(pos: Position) extends Statement
  case class SelectStatement(pos: Position, condition: Expression, cases: List[(List[Expression], BlockStatement)]) extends Statement
  case class SynchronizedStatement(pos: Position, condition: Expression, block: BlockStatement) extends Statement
  case class ThrowStatement(pos: Position, target: Expression) extends Statement
  case class TryStatement(pos: Position, tryBlock: BlockStatement, recClauses: List[(Argument, BlockStatement)], finBlock: Option[BlockStatement]) extends Statement
  case class WhileStatement(pos: Position, condition: Expression, block: BlockStatement) extends Statement
}  