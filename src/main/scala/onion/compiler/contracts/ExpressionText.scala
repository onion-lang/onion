package onion.compiler.contracts

import onion.compiler.AST

/**
 * A contract clause as the short source text a reader recognises, for the message of a run-time
 * guard (RFC #2557, design 7.4). Only the forms contracts are written in are covered; any other
 * form is shown as `...` rather than as a dump of the syntax tree.
 */
object ExpressionText {
  def of(e: AST.Expression): String = e match {
    case AST.Id(_, n)                       => n
    case AST.UnqualifiedFieldReference(_, n) => n
    case AST.IntegerLiteral(_, v, _)        => v.toString
    case AST.BooleanLiteral(_, v)           => v.toString
    case AST.StringLiteral(_, v)            => "\"" + v + "\""
    case AST.Not(_, t)                      => "!" + wrap(t)
    case AST.Negate(_, t)                   => "-" + wrap(t)
    case b: AST.BinaryExpression            => wrap(b.lhs) + " " + b.symbol + " " + wrap(b.rhs)
    case AST.UnqualifiedMethodCall(_, n, args, _) => n + args.map(of).mkString("(", ", ", ")")
    case AST.MemberSelection(_, t, n)       => (if (t == null) "" else of(t) + ".") + n
    case _                                  => "..."
  }

  /** Parenthesise an operand that is itself an operation, so the text keeps its meaning. */
  private def wrap(e: AST.Expression): String = e match {
    case _: AST.BinaryExpression => "(" + of(e) + ")"
    case _                       => of(e)
  }
}
