package onion.compiler.backend.asm

import onion.compiler.{LocalBinding, ClosureLocalBinding}
import onion.compiler.TypedAST
import onion.compiler.TypedAST.*

import scala.collection.mutable

private[compiler] object CapturedVariableCollector {
  /** Returns the outer class type if the statement tree (including nested
   *  closures) contains an OuterThis reference. */
  def findOuterThis(stmt: ActionStatement): Option[ClassType] = {
    var found: Option[ClassType] = None
    def scanTerm(term: Term): Unit = if (found.isEmpty) term match {
      case o: OuterThis => found = Some(o.`type`)
      case c: NewClosure => scanStatement(c.block)
      case other =>
        productChildren(other).foreach {
          case t: Term => scanTerm(t)
          case st: ActionStatement => scanStatement(st)
          case _ =>
        }
    }
    def scanStatement(statement: ActionStatement): Unit = if (found.isEmpty) {
      productChildren(statement).foreach {
        case t: Term => scanTerm(t)
        case st: ActionStatement => scanStatement(st)
        case seq: Seq[?] => seq.foreach {
          case t: Term => scanTerm(t)
          case st: ActionStatement => scanStatement(st)
          case _ =>
        }
        case arr: Array[?] => arr.foreach {
          case t: Term => scanTerm(t)
          case st: ActionStatement => scanStatement(st)
          case _ =>
        }
        case _ =>
      }
    }
    scanStatement(stmt)
    found
  }

  // One child enumeration for the whole compiler: TermWalk lists each node class's children
  // explicitly (reflection only for a class it does not know).
  private def productChildren(node: AnyRef): Seq[AnyRef] = onion.compiler.TermWalk.children(node)

  // A nested closure's captures are collected once for every enclosing closure (which relays
  // them) and once more when the nested closure itself is emitted; the walk is the same each
  // time, so the answer is memoized per body for the duration of one class's code generation.
  private val memo = new ThreadLocal[java.util.IdentityHashMap[ActionStatement, Seq[ClosureLocalBinding]]] {
    override def initialValue() = new java.util.IdentityHashMap
  }

  /** Forgets the answers of the previous code generation. */
  def reset(): Unit = if (!memo.get.isEmpty) memo.set(new java.util.IdentityHashMap)

  def collect(stmt: ActionStatement, frame: onion.compiler.LocalFrame = null): Seq[ClosureLocalBinding] = {
    val m = memo.get
    val cached = m.get(stmt)
    if (cached != null) return cached
    val fresh = collectUncached(stmt, frame)
    m.put(stmt, fresh)
    fresh
  }

  private def collectUncached(stmt: ActionStatement, frame: onion.compiler.LocalFrame): Seq[ClosureLocalBinding] = {
    // Use (frameIndex, index) as key to handle nested closures correctly
    val captured = mutable.LinkedHashMap[(Int, Int), ClosureLocalBinding]()

    // Build index -> ClosureLocalBinding map from frame
    val bindingsByIndex: Map[Int, ClosureLocalBinding] =
      if (frame != null) {
        frame.allBindings.collect {
          case cb: ClosureLocalBinding => cb.index -> cb
          case lb: LocalBinding => lb.index -> new ClosureLocalBinding(0, lb.index, lb.tp, lb.isMutable, lb.isBoxed)
        }.toMap
      } else Map.empty

    def record(frameIndex: Int, index: Int, tp: TypedAST.Type): Unit =
      captured.getOrElseUpdate((frameIndex, index), {
        // Create a new binding with the correct frameIndex
        // Use bindingsByIndex only for isMutable and isBoxed flags (if frame=1 means direct parent)
        val baseBinding = bindingsByIndex.get(index)
        new ClosureLocalBinding(
          frameIndex,  // Always use the passed frameIndex
          index,
          tp,
          baseBinding.map(_.isMutable).getOrElse(true),
          baseBinding.map(_.isBoxed).getOrElse(false)
        )
      })

    def visitTerm(term: Term): Unit = term match {
      case ref: RefLocal =>
        // Only capture variables from outer scopes (frame > 0)
        // frame = 0 means current scope (closure's own locals/parameters)
        if (ref.frame > 0) record(ref.frame, ref.index, ref.`type`)

      case set: SetLocal =>
        // Only capture variables from outer scopes (frame > 0)
        if (set.frame > 0) record(set.frame, set.index, set.`type`)
        visitTerm(set.value)

      case begin: Begin =>
        begin.terms.foreach(visitTerm)

      case unary: UnaryTerm =>
        visitTerm(unary.operand)

      case binary: BinaryTerm =>
        visitTerm(binary.lhs)
        visitTerm(binary.rhs)

      case call: Call =>
        visitTerm(call.target)
        call.parameters.foreach(visitTerm)

      case call: CallStatic =>
        call.parameters.foreach(visitTerm)

      case call: CallSuper =>
        visitTerm(call.target)
        call.params.foreach(visitTerm)

      case arrLength: ArrayLength =>
        visitTerm(arrLength.target)

      case refArray: RefArray =>
        visitTerm(refArray.target)
        visitTerm(refArray.index)

      case setArray: SetArray =>
        visitTerm(setArray.target)
        visitTerm(setArray.index)
        visitTerm(setArray.value)

      case cast: AsInstanceOf =>
        visitTerm(cast.target)

      case nn: NonNullAssert =>
        visitTerm(nn.target)

      case inst: InstanceOf =>
        visitTerm(inst.target)

      case list: ListLiteral =>
        list.elements.foreach(visitTerm)

      case map: MapLiteral =>
        map.keys.foreach(visitTerm)
        map.values.foreach(visitTerm)

      case refField: RefField =>
        visitTerm(refField.target)

      case setField: SetField =>
        visitTerm(setField.target)
        visitTerm(setField.value)

      case setStatic: SetStaticField =>
        visitTerm(setStatic.value)

      case newObj: NewObject =>
        newObj.parameters.foreach(visitTerm)

      case newArr: NewArray =>
        newArr.parameters.foreach(visitTerm)

      case newArrWithValues: NewArrayWithValues =>
        newArrWithValues.values.foreach(visitTerm)

      case closure: NewClosure =>
        // For nested closures, we need to capture variables that the nested closure will need
        // These are the variables that the nested closure itself captures (with adjusted frame indices)
        // Frame index adjustment: if nested closure captures at frame N, we need to capture at frame N-1
        // (because we're one level closer to the definition site)
        val nestedCaptured = CapturedVariableCollector.collect(closure.block, closure.frame)
        for capturedVar <- nestedCaptured do
          val adjustedFrame = capturedVar.frameIndex - 1
          if adjustedFrame > 0 then
            // This variable comes from an outer scope beyond the current closure
            record(adjustedFrame, capturedVar.index, capturedVar.tp)

      case stmt: StatementTerm =>
        visitStatement(stmt.statement)

      case _: BoolValue | _: ByteValue | _: CharacterValue | _: DoubleValue | _: FloatValue | _: IntValue |
          _: LongValue | _: ShortValue | _: StringValue | _: NullValue |
          _: RefStaticField | _: OuterThis | _: This =>
        ()

      case other =>
        // Defensive: recurse into any Term subtype not enumerated above (via its
        // Term/Statement-typed accessors) rather than throwing a MatchError, which
        // would surface as an I0000 internal error during codegen.
        productChildren(other).foreach {
          case t: Term => visitTerm(t)
          case s: ActionStatement => visitStatement(s)
          case _ => ()
        }
    }

    def visitStatement(statement: ActionStatement): Unit = statement match {
      case expr: ExpressionActionStatement =>
        visitTerm(expr.term)

      case block: StatementBlock =>
        block.statements.foreach(visitStatement)

      case ifStmt: IfStatement =>
        visitTerm(ifStmt.condition)
        visitStatement(ifStmt.thenStatement)
        ifStmt.elseStatement match {
          case null => ()
          case other => visitStatement(other)
        }

      case loop: ConditionalLoop =>
        visitTerm(loop.condition)
        visitStatement(loop.stmt)
        if loop.update != null then visitStatement(loop.update)

      case ret: Return =>
        if ret.term != null then visitTerm(ret.term)

      case sync: Synchronized =>
        if sync.term != null then visitTerm(sync.term)
        visitStatement(sync.statement)

      case thr: Throw =>
        visitTerm(thr.term)

      case tr: Try =>
        tr.resources.foreach { case (_, init) => visitTerm(init) }
        visitStatement(tr.tryStatement)
        tr.catchStatements.foreach(visitStatement)
        if (tr.finallyStatement != null) visitStatement(tr.finallyStatement)

      case _: Break | _: Continue | _: NOP =>
        ()
    }

    visitStatement(stmt)
    captured.values.toSeq
  }
}
