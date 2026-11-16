package onion.compiler.typing

import onion.compiler.TypedAST.*

/**
 * Whether a statement can complete normally. One definition for the method rule
 * (E0067) and for a lambda's void tail (#3044): a tail that never completes needs no value.
 * Conservative: true only when certain, so a program is never wrongly rejected.
 */
object StatementReachability {
  def definitelyReturns(stmt: ActionStatement): Boolean = stmt match {
    case _: Return => true
    case _: Throw => true
    case b: StatementBlock =>
      val ss = b.statements
      ss.nonEmpty && definitelyReturns(ss(ss.length - 1))
    case i: IfStatement =>
      i.elseStatement != null && definitelyReturns(i.thenStatement) && definitelyReturns(i.elseStatement)
    case loop: ConditionalLoop =>
      // An infinite loop (while/for over a constant-true or absent condition)
      // never completes normally. A `break` would make this a false negative,
      // which is safe (we never wrongly reject), so we do not scan for one.
      loop.condition == null || (loop.condition match { case b: BoolValue => b.value; case _ => false })
    case t: Try =>
      (t.finallyStatement != null && definitelyReturns(t.finallyStatement)) ||
        (definitelyReturns(t.tryStatement) && t.catchStatements.forall(definitelyReturns))
    case s: Synchronized =>
      // A synchronized block returns exactly when its body does; without this a
      // `synchronized(l) { return x }` was wrongly flagged as missing a return.
      definitelyReturns(s.statement)
    case e: ExpressionActionStatement => e.term.`type`.isBottomType
    case _ => false
  }
}
