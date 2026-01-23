package onion.compiler.bytecode

import onion.compiler.{LocalBinding, ClosureLocalBinding}
import onion.compiler.TypedAST
import onion.compiler.TypedAST.*

import scala.collection.mutable

private[compiler] object CapturedVariableCollector {
  def collect(stmt: ActionStatement, frame: onion.compiler.LocalFrame = null): Seq[ClosureLocalBinding] = {
    // Use (frameIndex, index) as key to handle nested closures correctly
    val captured = mutable.LinkedHashMap[(Int, Int), ClosureLocalBinding]()

    // Build index -> ClosureLocalBinding map from frame
    val bindingsByIndex: Map[Int, ClosureLocalBinding] =
      if (frame != null) {
        frame.entries.collect {
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

      case inst: InstanceOf =>
        visitTerm(inst.target)

      case list: ListLiteral =>
        list.elements.foreach(visitTerm)

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

      case _: BoolValue | _: ByteValue | _: CharacterValue | _: DoubleValue | _: FloatValue | _: IntValue |
          _: LongValue | _: ShortValue | _: StringValue | _: NullValue |
          _: RefStaticField | _: OuterThis | _: This =>
        ()
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

