package onion.compiler

import TypedAST._

/**
 * Visitor pattern for TypedAST traversal.
 * This trait defines visit methods for all TypedAST node types.
 */
trait TypedASTVisitor[T]:
  // Expression visitors
  def visitArrayLength(node: ArrayLength): T
  def visitRefArray(node: RefArray): T
  def visitSetArray(node: SetArray): T
  def visitBegin(node: Begin): T
  def visitBinaryTerm(node: BinaryTerm): T
  def visitBoolValue(node: BoolValue): T
  def visitByteValue(node: ByteValue): T
  def visitCall(node: Call): T
  def visitCallStatic(node: CallStatic): T
  def visitCallSuper(node: CallSuper): T
  def visitAsInstanceOf(node: AsInstanceOf): T
  def visitCharacterValue(node: CharacterValue): T
  def visitDoubleValue(node: DoubleValue): T
  def visitFloatValue(node: FloatValue): T
  def visitInstanceOf(node: InstanceOf): T
  def visitIntValue(node: IntValue): T
  def visitListLiteral(node: ListLiteral): T
  def visitRefLocal(node: RefLocal): T
  def visitSetLocal(node: SetLocal): T
  def visitNewClosure(node: NewClosure): T
  def visitLongValue(node: LongValue): T
  def visitRefField(node: RefField): T
  def visitSetField(node: SetField): T
  def visitNewObject(node: NewObject): T
  def visitNewArray(node: NewArray): T
  def visitNullValue(node: NullValue): T
  def visitShortValue(node: ShortValue): T
  def visitRefStaticField(node: RefStaticField): T
  def visitSetStaticField(node: SetStaticField): T
  def visitStringValue(node: StringValue): T
  def visitOuterThis(node: OuterThis): T
  def visitThis(node: This): T
  def visitUnaryTerm(node: UnaryTerm): T
  def visitStatementTerm(node: StatementTerm): T
  def visitSynchronizedTerm(node: SynchronizedTerm): T

  // Statement visitors
  def visitStatementBlock(node: StatementBlock): T
  def visitBreak(node: Break): T
  def visitContinue(node: Continue): T
  def visitExpressionActionStatement(node: ExpressionActionStatement): T
  def visitIfStatement(node: IfStatement): T
  def visitConditionalLoop(node: ConditionalLoop): T
  def visitNOP(node: NOP): T
  def visitReturn(node: Return): T
  def visitSynchronized(node: Synchronized): T
  def visitThrow(node: Throw): T
  def visitTry(node: Try): T
  
  // Helper method to visit any Term
  def visitTerm(term: Term): T = term match
    case n: ArrayLength => visitArrayLength(n)
    case n: RefArray => visitRefArray(n)
    case n: SetArray => visitSetArray(n)
    case n: Begin => visitBegin(n)
    case n: BinaryTerm => visitBinaryTerm(n)
    case n: BoolValue => visitBoolValue(n)
    case n: ByteValue => visitByteValue(n)
    case n: Call => visitCall(n)
    case n: CallStatic => visitCallStatic(n)
    case n: CallSuper => visitCallSuper(n)
    case n: AsInstanceOf => visitAsInstanceOf(n)
    case n: CharacterValue => visitCharacterValue(n)
    case n: DoubleValue => visitDoubleValue(n)
    case n: FloatValue => visitFloatValue(n)
    case n: InstanceOf => visitInstanceOf(n)
    case n: IntValue => visitIntValue(n)
    case n: ListLiteral => visitListLiteral(n)
    case n: RefLocal => visitRefLocal(n)
    case n: SetLocal => visitSetLocal(n)
    case n: NewClosure => visitNewClosure(n)
    case n: LongValue => visitLongValue(n)
    case n: RefField => visitRefField(n)
    case n: SetField => visitSetField(n)
    case n: NewObject => visitNewObject(n)
    case n: NewArray => visitNewArray(n)
    case n: NullValue => visitNullValue(n)
    case n: ShortValue => visitShortValue(n)
    case n: RefStaticField => visitRefStaticField(n)
    case n: SetStaticField => visitSetStaticField(n)
    case n: StringValue => visitStringValue(n)
    case n: OuterThis => visitOuterThis(n)
    case n: This => visitThis(n)
    case n: UnaryTerm => visitUnaryTerm(n)
    case n: StatementTerm => visitStatementTerm(n)
    case n: SynchronizedTerm => visitSynchronizedTerm(n)

  // Helper method to visit any ActionStatement
  def visitStatement(stmt: ActionStatement): T = stmt match
    case n: StatementBlock => visitStatementBlock(n)
    case n: Break => visitBreak(n)
    case n: Continue => visitContinue(n)
    case n: ExpressionActionStatement => visitExpressionActionStatement(n)
    case n: IfStatement => visitIfStatement(n)
    case n: ConditionalLoop => visitConditionalLoop(n)
    case n: NOP => visitNOP(n)
    case n: Return => visitReturn(n)
    case n: Synchronized => visitSynchronized(n)
    case n: Throw => visitThrow(n)
    case n: Try => visitTry(n)

/**
 * Base implementation of TypedASTVisitor that provides no-op default implementations.
 * Subclasses can override only the methods they need.
 */
abstract class DefaultTypedASTVisitor[T] extends TypedASTVisitor[T]:
  def defaultValue: T
  
  override def visitArrayLength(node: ArrayLength): T = defaultValue
  override def visitRefArray(node: RefArray): T = defaultValue
  override def visitSetArray(node: SetArray): T = defaultValue
  override def visitBegin(node: Begin): T = defaultValue
  override def visitBinaryTerm(node: BinaryTerm): T = defaultValue
  override def visitBoolValue(node: BoolValue): T = defaultValue
  override def visitByteValue(node: ByteValue): T = defaultValue
  override def visitCall(node: Call): T = defaultValue
  override def visitCallStatic(node: CallStatic): T = defaultValue
  override def visitCallSuper(node: CallSuper): T = defaultValue
  override def visitAsInstanceOf(node: AsInstanceOf): T = defaultValue
  override def visitCharacterValue(node: CharacterValue): T = defaultValue
  override def visitDoubleValue(node: DoubleValue): T = defaultValue
  override def visitFloatValue(node: FloatValue): T = defaultValue
  override def visitInstanceOf(node: InstanceOf): T = defaultValue
  override def visitIntValue(node: IntValue): T = defaultValue
  override def visitListLiteral(node: ListLiteral): T = defaultValue
  override def visitRefLocal(node: RefLocal): T = defaultValue
  override def visitSetLocal(node: SetLocal): T = defaultValue
  override def visitNewClosure(node: NewClosure): T = defaultValue
  override def visitLongValue(node: LongValue): T = defaultValue
  override def visitRefField(node: RefField): T = defaultValue
  override def visitSetField(node: SetField): T = defaultValue
  override def visitNewObject(node: NewObject): T = defaultValue
  override def visitNewArray(node: NewArray): T = defaultValue
  override def visitNullValue(node: NullValue): T = defaultValue
  override def visitShortValue(node: ShortValue): T = defaultValue
  override def visitRefStaticField(node: RefStaticField): T = defaultValue
  override def visitSetStaticField(node: SetStaticField): T = defaultValue
  override def visitStringValue(node: StringValue): T = defaultValue
  override def visitOuterThis(node: OuterThis): T = defaultValue
  override def visitThis(node: This): T = defaultValue
  override def visitUnaryTerm(node: UnaryTerm): T = defaultValue
  override def visitStatementTerm(node: StatementTerm): T = defaultValue
  override def visitSynchronizedTerm(node: SynchronizedTerm): T = defaultValue
  override def visitStatementBlock(node: StatementBlock): T = defaultValue
  override def visitBreak(node: Break): T = defaultValue
  override def visitContinue(node: Continue): T = defaultValue
  override def visitExpressionActionStatement(node: ExpressionActionStatement): T = defaultValue
  override def visitIfStatement(node: IfStatement): T = defaultValue
  override def visitConditionalLoop(node: ConditionalLoop): T = defaultValue
  override def visitNOP(node: NOP): T = defaultValue
  override def visitReturn(node: Return): T = defaultValue
  override def visitSynchronized(node: Synchronized): T = defaultValue
  override def visitThrow(node: Throw): T = defaultValue
  override def visitTry(node: Try): T = defaultValue
