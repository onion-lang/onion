package onion.compiler

import onion.compiler.TypedAST.*

/**
 * A structural walk over typed terms and statements.
 *
 * TypedAST nodes are plain classes rather than case classes, so there is no
 * `productIterator` to lean on; the children are found through each node's public
 * zero-argument accessors that return a `Term`, an `ActionStatement`, an array, or a
 * Java list. That is the same trick `CapturedVariableCollector` and the ASM backend's
 * try-detector use, gathered in one place so a new predicate over the tree does not
 * bring a third copy of the reflection with it.
 *
 * Closure bodies are not entered: a closure compiles to its own method, so anything a
 * caller wants to know about *this* method's evaluation -- what is on its operand
 * stack, whether `this` is touched before it is initialized -- stops at the closure
 * boundary. Callers that do want to look inside pass `intoClosures = true`.
 */
object TermWalk:

  /** Whether `predicate` holds for `term` or any term or statement beneath it. */
  def exists(term: Term, intoClosures: Boolean = false)(predicate: AnyRef => Boolean): Boolean =
    walk(term, intoClosures, predicate)

  /** Whether `predicate` holds for `statement` or any term or statement beneath it. */
  def existsIn(statement: ActionStatement, intoClosures: Boolean = false)(predicate: AnyRef => Boolean): Boolean =
    walk(statement, intoClosures, predicate)

  private def walk(node: AnyRef, intoClosures: Boolean, predicate: AnyRef => Boolean): Boolean =
    if predicate(node) then true
    else
      node match
        case _: NewClosure if !intoClosures => false
        case stmt: StatementTerm => walk(stmt.statement, intoClosures, predicate)
        case other =>
          children(other).exists {
            case t: Term => walk(t, intoClosures, predicate)
            case s: ActionStatement => walk(s, intoClosures, predicate)
            case _ => false
          }

  /**
   * The direct children of a node: every term and statement it holds. Written out per node
   * class -- the reflective walk below did the same through each class's accessors, at the
   * cost of a `Method.invoke` per accessor per node -- and the reflection stays only as the
   * fallback for a node class not listed here.
   */
  private[compiler] def children(node: AnyRef): Seq[AnyRef] = node match
    // leaves
    case _: RefLocal | _: This | _: OuterThis | _: NullValue | _: IntValue | _: LongValue | _: DoubleValue |
         _: FloatValue | _: BoolValue | _: ByteValue | _: ShortValue | _: CharacterValue | _: StringValue |
         _: RefStaticField | _: NOP | _: Break | _: Continue => Nil
    // one child
    case n: ArrayLength => n.target :: Nil
    case n: NonNullAssert => n.target :: Nil
    case n: AsInstanceOf => n.target :: Nil
    case n: InstanceOf => n.target :: Nil
    case n: RefField => n.target :: Nil
    case n: SafeFieldAccess => n.target :: Nil
    case n: SetLocal => n.value :: Nil
    case n: SetStaticField => n.value :: Nil
    case n: UnaryTerm => n.operand :: Nil
    case n: ExpressionActionStatement => n.term :: Nil
    case n: Return => if n.term == null then Nil else n.term :: Nil
    case n: Throw => n.term :: Nil
    case n: StatementTerm => n.statement :: Nil
    case n: NewClosure => n.block :: Nil
    // two or three children
    case n: RefArray => n.target :: n.index :: Nil
    case n: SafeRefArray => n.target :: n.index :: Nil
    case n: SetArray => n.target :: n.index :: n.value :: Nil
    case n: BinaryTerm => n.lhs :: n.rhs :: Nil
    case n: SetField => n.target :: n.value :: Nil
    case n: SynchronizedTerm => n.lock :: n.body :: Nil
    case n: Synchronized => n.term :: n.statement :: Nil
    case n: IfStatement => if n.elseStatement == null then n.condition :: n.thenStatement :: Nil else n.condition :: n.thenStatement :: n.elseStatement :: Nil
    case n: ConditionalLoop => if n.update == null then n.condition :: n.stmt :: Nil else n.condition :: n.stmt :: n.update :: Nil
    // arrays of children
    case n: Begin => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.terms)
    case n: StatementBlock => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.statements)
    case n: Call => n.target :: scala.collection.immutable.ArraySeq.unsafeWrapArray(n.parameters).toList
    case n: SafeCall => n.target :: scala.collection.immutable.ArraySeq.unsafeWrapArray(n.parameters).toList
    case n: CallSuper => n.target :: scala.collection.immutable.ArraySeq.unsafeWrapArray(n.params).toList
    case n: CallStatic => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.parameters)
    case n: NewObject => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.parameters)
    case n: NewArray => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.parameters)
    case n: NewArrayWithValues => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.values)
    case n: ListLiteral => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.elements)
    case n: MapLiteral => scala.collection.immutable.ArraySeq.unsafeWrapArray(n.keys) ++ scala.collection.immutable.ArraySeq.unsafeWrapArray(n.values)
    case n: Try =>
      // Resource initializers are held as (binding, term) pairs; the reflective walk saw the
      // pairs, not the terms, and never entered them -- kept as is.
      val out = new scala.collection.mutable.ArrayBuffer[AnyRef](2 + n.catchStatements.length)
      if n.tryStatement != null then out += n.tryStatement
      out ++= n.catchStatements
      if n.finallyStatement != null then out += n.finallyStatement
      out.toSeq
    case p: Product => p.productIterator.collect { case r: AnyRef => r }.toSeq
    case _ => reflectiveChildren(node)

  // The accessor set of a node class never changes, and getMethods() plus the filter is
  // the expensive part of a reflective walk -- it ran for every node on every walk, and
  // codegen walks the right-hand side of every assignment. One lookup per class instead.
  private val accessors = new java.util.concurrent.ConcurrentHashMap[Class[?], Array[java.lang.reflect.Method]]()

  private def accessorsOf(cls: Class[?]): Array[java.lang.reflect.Method] =
    accessors.computeIfAbsent(cls, c =>
      c.getMethods.iterator
        .filter(m => m.getParameterCount == 0 && !m.getName.contains("$") &&
          (classOf[Term].isAssignableFrom(m.getReturnType) ||
           classOf[ActionStatement].isAssignableFrom(m.getReturnType) ||
           m.getReturnType.isArray ||
           classOf[java.util.List[?]].isAssignableFrom(m.getReturnType)))
        .toArray)

  private def reflectiveChildren(node: AnyRef): Seq[AnyRef] =
    // One buffer, filled by index: the iterator/flatMap/toSeq chain it replaces allocated
    // several collections per node on every walk.
    val methods = accessorsOf(node.getClass)
    val out = new scala.collection.mutable.ArrayBuffer[AnyRef](methods.length)
    var i = 0
    while i < methods.length do
      try
        methods(i).invoke(node) match
          case null =>
          case arr: Array[?] =>
            var j = 0
            while j < arr.length do
              arr(j) match
                case r: AnyRef => out += r
                case _ =>
              j += 1
          case list: java.util.List[?] =>
            val it = list.iterator()
            while it.hasNext do
              it.next() match
                case r: AnyRef => out += r
                case _ =>
          case other: AnyRef => out += other
      catch case _: Throwable => ()
      i += 1
    out.toSeq
