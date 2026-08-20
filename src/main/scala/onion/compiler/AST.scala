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
  val M_SYNTHETIC_RECORD = 8192  // Auto-generated record methods (equals, hashCode, toString, copy)
  def hasModifier(bitFlags: Int, modifier: Int): Boolean = (bitFlags & modifier) != 0
  def append[A](buffer: scala.collection.mutable.Buffer[A], element: A): Unit = {
    buffer += element
  }

  /** Append an element to an immutable list (returns new list) - used for trailing lambda */
  def appendToList[A](list: List[A], element: A): List[A] = {
    if (element == null) list else list :+ element
  }

  /** Prepend an element to an immutable list (returns new list) - used by the |> pipeline desugar */
  def prependToList[A](list: List[A], element: A): List[A] = element +: list

  /**
   * Java-callable factory for a record declaration with an optional `from re"..."` clause.
   * `fromRaw` is the raw regex body (no `re"`/`"` delimiters) or null when the clause is absent.
   */
  def recordDeclaration(
    location: Location, modifiers: Int, name: String,
    typeParameters: List[TypeParameter], args: List[Argument],
    superInterfaces: List[TypeNode], fromRaw: String, derives: List[String],
    laws: List[LawClause], examples: List[ExampleClause],
    sections: List[AccessSection], shapes: List[ShapeClause]
  ): RecordDeclaration =
    RecordDeclaration(location, modifiers, name, typeParameters, args, superInterfaces, Option(fromRaw), derives, laws, examples, Nil, sections, shapes)

  /** Java-callable factory for an `example` clause; `name` may be null (unnamed example). */
  def exampleClause(location: Location, name: String, body: BlockExpression): ExampleClause =
    ExampleClause(location, Option(name), body)
  /** Java-callable factory for a top-level `example`; `name` may be null. */
  def topLevelExample(location: Location, name: String, body: BlockExpression): TopLevelExample =
    TopLevelExample(location, Option(name), body)
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
  /** Wildcard type: ?, ? extends T, or ? super T */
  case class WildcardType(upperBound: Option[TypeDescriptor], lowerBound: Option[TypeDescriptor]) extends TypeDescriptor {
    override def toString: String = (upperBound, lowerBound) match {
      case (None, None) => "?"
      case (Some(ub), None) => s"? extends $ub"
      case (None, Some(lb)) => s"? super $lb"
      case (Some(ub), Some(lb)) => s"? extends $ub super $lb" // unusual but representable
    }
  }
  /** Nullable type: T? */
  case class NullableType(inner: TypeDescriptor) extends TypeDescriptor {
    override def toString: String = s"${inner.toString}?"
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
  case class Argument(location: Location, name: String, typeRef: TypeNode, defaultValue: Expression = null, isVararg: Boolean = false) extends Node
  case class CompilationUnit(
    location: Location, sourceFile: String/*nullable*/, module: ModuleDeclaration/*nullable*/,
    imports: ImportClause, toplevels: List[Toplevel]) extends Node
  case class ModuleDeclaration(location: Location, name: String) extends Node
  case class ImportClause(
    location: Location,
    mapping: List[(String, String)],
    staticImports: List[(String, String)] = Nil
  ) extends Node
  abstract sealed class Toplevel extends Node
  abstract sealed class BlockElement extends Toplevel
  abstract sealed class Expression extends BlockElement
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
  case class Elvis(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("?:")
  case class Equal(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("==")
  case class FloatLiteral(location: Location, value: Float) extends Expression
  case class GreaterOrEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">=")
  case class GreaterThan(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression(">")
  case class Id(location: Location, name: String) extends Expression
  case class Indexing(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("[]")
  case class SafeIndexing(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("?[]")
  case class IntegerLiteral(location: Location, value: Int) extends Expression
  case class IsInstance(location: Location, target: Expression, typeRef: TypeNode) extends Expression
  case class LessOrEqual(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<=")
  case class LeftShiftAssignment(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<<=")
  case class LessThan(location: Location, lhs: Expression, rhs: Expression) extends BinaryExpression("<")
  case class ListLiteral(location: Location, elements: List[Expression]) extends Expression
  case class MapLiteral(location: Location, entries: List[(Expression, Expression)]) extends Expression
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
  /** Safe member selection: expr?.name (returns null if expr is null) */
  case class SafeMemberSelection(location: Location, target: Expression, name: String) extends Expression
  /** Safe method call: expr?.name(args) (returns null if expr is null) */
  case class SafeMethodCall(location: Location, target: Expression, name: String, args: List[Expression], typeArgs: List[TypeNode] = Nil) extends Expression {
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
  case class NotNullAssertion(location: Location, term: Expression) extends UnaryExpression("!!")
  case class BitNot(location: Location, term: Expression) extends UnaryExpression("~")
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
  /** Type-class dictionary access `Trait[TypeArgs]::method(args)`. Lowered in
    * Rewriting to a call on the instance for the (ground) type arguments; the
    * dictionary-passing form for abstract type parameters is a later stage. */
  case class TraitMethodCall(location: Location, traitType: TypeNode, typeArgs: List[TypeNode], name: String, args: List[Expression]) extends Expression
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

  // Do notation for monadic operations: do[M] { x <- e1; y <- e2; ret e3 }
  /** Binding statement in do notation: x <- expr */
  case class DoBinding(location: Location, name: String, expr: Expression) extends Node
  /** Return statement in do notation: ret expr */
  case class RetStatement(location: Location, expr: Expression) extends Expression
  /** Do expression: do[M] { statements }
   *  statements is a List of DoBinding or Expression (including RetStatement) */
  case class DoExpression(location: Location, monadType: TypeNode, statements: List[Any]) extends Expression

  // Patterns for select/case
  abstract sealed class Pattern extends Node
  case class ExpressionPattern(expr: Expression) extends Pattern { def location: Location = expr.location }
  case class TypePattern(location: Location, name: String, typeRef: TypeNode) extends Pattern
  case class WildcardPattern(location: Location) extends Pattern
  case class DestructuringPattern(location: Location, constructor: String, bindings: List[Pattern]) extends Pattern
  // BindingPattern is a simple variable binding (used inside destructuring)
  case class BindingPattern(location: Location, name: String) extends Pattern
  /** re"..." (g1, g2, ...) — matches a String against a regex literal and binds its capture groups. */
  case class RegexPattern(location: Location, pattern: String, bindings: List[String]) extends Pattern
  case class GuardedPattern(location: Location, pattern: Pattern, guard: Expression) extends Pattern

  abstract sealed class ForInitializer extends Node
  case class ForInitDeclaration(declaration: LocalVariableDeclaration) extends ForInitializer {
    def location: Location = declaration.location
  }
  case class ForInitExpression(expression: Expression) extends ForInitializer {
    def location: Location = expression.location
  }
  case class ForInitEmpty(location: Location) extends ForInitializer

  case class BlockExpression(location: Location, elements: List[BlockElement]) extends Expression
  case class BreakExpression(location: Location, label: String = null) extends Expression
  case class ContinueExpression(location: Location, label: String = null) extends Expression
  /** label: while/for/foreach/do — target for labeled break/continue */
  case class LabeledLoop(location: Location, name: String, loop: Expression) extends Expression
  case class ForeachExpression(location: Location, arg: Argument, collection: Expression, statement: BlockExpression) extends Expression
  case class ForExpression(location: Location, init: ForInitializer, condition: Expression /*nullable*/ , update: Expression /*nullable*/ , block: BlockExpression) extends Expression
  case class IfExpression(location: Location, condition: Expression, thenBlock: BlockExpression, elseBlock: BlockExpression /*nullable*/) extends Expression
  case class LocalVariableDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode/*nullable*/, init: Expression/*nullable*/) extends BlockElement
  /** val (a, b) = expr — positional destructuring of records / Map.Entry */
  case class DestructuringDeclaration(location: Location, modifiers: Int, names: List[String], init: Expression) extends BlockElement
  case class ReturnExpression(location: Location, result: Expression /*nullable*/) extends Expression
  case class SelectExpression(location: Location, condition: Expression, cases: List[(List[Pattern], BlockExpression)], elseBlock: BlockExpression /*nullable*/) extends Expression
  case class SynchronizedExpression(location: Location, condition: Expression, block: BlockExpression) extends Expression
  case class ThrowExpression(location: Location, target: Expression) extends Expression
  case class TryExpression(location: Location, resources: List[LocalVariableDeclaration], tryBlock: BlockExpression, recClauses: List[(Argument, BlockExpression)], finBlock: BlockExpression /*nullable*/) extends Expression
  case class DoWhileExpression(location: Location, block: BlockExpression, condition: Expression) extends Expression
  case class WhileExpression(location: Location, condition: Expression, block: BlockExpression) extends Expression

  case class FunctionDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, typeParameters: List[TypeParameter] = Nil, throwsTypes: List[TypeNode] = Nil, annotations: List[Annotation] = Nil) extends Toplevel {
    def this(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, throwsTypes: List[TypeNode]) =
      this(location, modifiers, name, args, returnType, block, Nil, throwsTypes, Nil)
  }
  case class GlobalVariableDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends Toplevel

  /**
   * Type alias declaration: type Name[A, B] = TargetType
   */
  case class TypeAliasDeclaration(location: Location, modifiers: Int, name: String, typeParameters: List[TypeParameter], targetType: TypeNode) extends Toplevel

  /**
   * Extension declaration for adding methods to existing types.
   *
   * {{{
   * extension String {
   *   def reversed(): String { ... }
   * }
   * }}}
   *
   * @param location Source location
   * @param receiverType The type being extended
   * @param methods Methods to add to the type
   */
  case class ExtensionDeclaration(location: Location, receiverType: TypeNode, methods: List[MethodDeclaration]) extends Toplevel
  /** `instance Trait[Type] { ... }`. Lowered in Rewriting to a class implementing
    * the trait interface; the type-class registry/singleton machinery is layered
    * on in later stages. */
  case class InstanceDeclaration(location: Location, modifiers: Int, traitType: TypeNode, methods: List[MethodDeclaration]) extends Toplevel

  abstract sealed class MemberDeclaration extends Node { def modifiers: Int; def name: String }
  case class TypeParameter(location: Location, name: String, upperBound: Option[TypeNode] = None, constraints: List[TypeNode] = Nil) extends Node
  case class Annotation(location: Location, name: String) extends Node

  case class MethodDeclaration(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, typeParameters: List[TypeParameter] = Nil, throwsTypes: List[TypeNode] = Nil, annotations: List[Annotation] = Nil) extends MemberDeclaration {
    def this(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression) =
      this(location, modifiers, name, args, returnType, block, Nil, Nil, Nil)
    def this(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, typeParameters: List[TypeParameter]) =
      this(location, modifiers, name, args, returnType, block, typeParameters, Nil, Nil)
    def this(location: Location, modifiers: Int, name: String, args: List[Argument], returnType: TypeNode, block: BlockExpression, typeParameters: List[TypeParameter], throwsTypes: List[TypeNode]) =
      this(location, modifiers, name, args, returnType, block, typeParameters, throwsTypes, Nil)
  }
  case class FieldDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression/*nullable*/) extends MemberDeclaration
  case class DelegatedFieldDeclaration(location: Location, modifiers: Int, name: String, typeRef: TypeNode, init: Expression) extends MemberDeclaration
  /**
   * A constructor, in one of three shapes the parser produces:
   *
   *   - the '''primary''' constructor, synthesized from `class C(params) extends B(args)`:
   *     `primary = true`, `superInits = args`, and `block` holds exactly
   *     `primaryAssignments` field assignments (`this.x = x` for each `val`/`var` param)
   *     and nothing else — the typer relies on that count to place declared field
   *     initializers ''after'' those assignments;
   *   - a '''secondary''' constructor delegating to a sibling, `def this(..) : this(args) { .. }`:
   *     `selfDelegation = true`, `superInits = args`;
   *   - a secondary constructor with no delegation clause, `def this(..) { .. }`: neither flag,
   *     `superInits` empty, and it calls the superclass's no-arg constructor.
   *
   * There is no shape for a secondary constructor that passes arguments to the superclass:
   * arguments to a super constructor are written once, on the `extends` clause, and belong
   * to the primary. A `def this` in a class that ''has'' a primary must delegate to it.
   */
  case class ConstructorDeclaration(location: Location, modifiers: Int, args: List[Argument], superInits: List[Expression], block: BlockExpression, selfDelegation: Boolean = false, primary: Boolean = false, primaryAssignments: Int = 0) extends MemberDeclaration {val name = "new" }

  case class AccessSection(location: Location, modifiers: Int, members: List[MemberDeclaration]) extends Node
  abstract sealed class TypeDeclaration extends Toplevel { def modifiers: Int; def name: String }
  /**
   * @param fromPattern        Raw regex from a `record ... from re"..."` clause, used to
   *                           synthesize the static `parse`/`parseAll` methods (None when absent).
   * @param synthesizedMethods Methods derived from `fromPattern` (filled in during Rewriting).
   */
  case class RecordDeclaration(location: Location, modifiers: Int, name: String, typeParameters: List[TypeParameter], args: List[Argument], superInterfaces: List[TypeNode] = Nil, fromPattern: Option[String] = None, derives: List[String] = Nil, laws: List[LawClause] = Nil, examples: List[ExampleClause] = Nil, synthesizedMethods: List[MethodDeclaration] = Nil, sections: List[AccessSection] = Nil, shapes: List[ShapeClause] = Nil) extends TypeDeclaration
  /**
   * A `shape name = re"..."` clause on a record — a named, first-class boundary.
   *
   * Unlike `from re"..."`, which allows one pattern per record and bolts fixed-name
   * statics onto it, a record may carry as many shape clauses as it needs: a v1 and a v2
   * log format can coexist, each reached by its own name.
   */
  case class ShapeClause(location: Location, name: String, source: ShapeSource) extends Node
  /** What a shape clause reads: an inline regex, or a named document format. */
  sealed trait ShapeSource
  case class RegexSource(pattern: String) extends ShapeSource
  /** `json` / `yaml` — a structured document whose keys are the component names. */
  case class FormatSource(format: String) extends ShapeSource
  /** Java-callable factories for the two shape sources. */
  def regexShapeClause(location: Location, name: String, pattern: String): ShapeClause =
    ShapeClause(location, name, RegexSource(pattern))
  def formatShapeClause(location: Location, name: String, format: String): ShapeClause =
    ShapeClause(location, name, FormatSource(format))
  /** A `law name(p: T) { boolean-expr }` clause on a record — a compile-time property check. */
  case class LawClause(location: Location, name: String, params: List[Argument], body: BlockExpression) extends Node
  /** An `example { boolean-expr }` clause on a record — a compile-time concrete check. */
  case class ExampleClause(location: Location, name: Option[String], body: BlockExpression) extends Node
  /** A top-level `example { boolean-expr }`, desugared to a static boolean method in <File>Main. */
  case class TopLevelExample(location: Location, name: Option[String], body: BlockExpression) extends Toplevel
  /**
   * @param hasPrimary whether the class declared a primary constructor — a parameter list
   *                   after the name, or arguments on the `extends` clause, or both. When it
   *                   did, every `def this` in the body must delegate to it with `: this(..)`.
   *                   A class with neither has no primary; its `def this` constructors call
   *                   the superclass's no-arg constructor directly, as they always have.
   */
  case class ClassDeclaration(location: Location, modifiers: Int, name: String, superClass: TypeNode, superInterfaces: List[TypeNode], defaultSection: Option[AccessSection], sections: List[AccessSection], typeParameters: List[TypeParameter] = Nil, hasPrimary: Boolean = false) extends TypeDeclaration {
    def this(location: Location, modifiers: Int, name: String, superClass: TypeNode, superInterfaces: List[TypeNode], defaultSection: Option[AccessSection], sections: List[AccessSection]) =
      this(location, modifiers, name, superClass, superInterfaces, defaultSection, sections, Nil, false)
  }
  case class InterfaceDeclaration(location: Location, modifiers: Int, name: String, superInterfaces: List[TypeNode], methods: List[MethodDeclaration], typeParameters: List[TypeParameter] = Nil) extends TypeDeclaration {
    def this(location: Location, modifiers: Int, name: String, superInterfaces: List[TypeNode], methods: List[MethodDeclaration]) =
      this(location, modifiers, name, superInterfaces, methods, Nil)
  }

  /**
   * An enum constant in an enum declaration.
   *
   * `args` are the value-expression arguments of a homogeneous shared-param
   * enum constant (`EARTH(5.97)`); empty for a bare constant (`RED`).
   *
   * `caseFields` distinguishes a Scala-3-style ADT `case` from a homogeneous
   * constant: `None` = bare/homogeneous constant, `Some(fields)` = ADT case
   * carrying its OWN typed fields (record-style `name: Type`). An empty
   * `Some(Nil)` is a singleton ADT case (`case Origin`).
   */
  case class EnumConstant(location: Location, name: String, args: List[Expression], caseFields: Option[List[Argument]] = None) extends Node {
    /** True iff this constant was introduced with the `case` keyword (ADT case). */
    def isCase: Boolean = caseFields.isDefined
  }

  /** Java-callable factory for a bare/homogeneous enum constant. */
  def enumConstant(location: Location, name: String, args: List[Expression]): EnumConstant =
    EnumConstant(location, name, args, None)

  /** Java-callable factory for an ADT `case` enum constant (product or singleton). */
  def enumCaseConstant(location: Location, name: String, fields: List[Argument]): EnumConstant =
    EnumConstant(location, name, Nil, Some(fields))

  /** An enum type declaration */
  case class EnumDeclaration(location: Location, modifiers: Int, name: String, params: List[Argument], constants: List[EnumConstant], sections: List[AccessSection] = Nil, typeParameters: List[TypeParameter] = Nil) extends TypeDeclaration
}
