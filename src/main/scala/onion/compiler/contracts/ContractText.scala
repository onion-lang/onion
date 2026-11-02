package onion.compiler.contracts

import onion.compiler.AST
import onion.compiler.parser.OnionParser

/**
 * A contract clause as text that survives a trip through a `.class` file (RFC #2557, design 5).
 *
 * The clauses of a compiled method or class are stored as source text in an annotation, and read
 * back by parsing that text again. A clause that cannot be written as text the parser reads back
 * to the same expression is not stored at all: a module may omit a contract, but it must never
 * pass on a different one.
 */
object ContractText {
  /** The text of `e`, or none when it would not read back as the same expression. */
  def write(e: AST.Expression): Option[String] = {
    val text = ExpressionText.of(e)
    if (text.contains("...")) None
    else read(text).filter(same(_, e)).map(_ => text)
  }

  /** The expression a stored clause reads back as, or none when it does not parse as one. */
  def read(text: String): Option[AST.Expression] =
    try {
      val unit = OnionParser.parse("val __contract = " + text + "\n")
      unit.toplevels.collectFirst {
        case AST.LocalVariableDeclaration(_, _, "__contract", _, init) if init != null => init
      }
    } catch { case _: Throwable => None }

  /** Two clauses are the same when they print the same: the text is the contract's identity. */
  private def same(a: AST.Expression, b: AST.Expression): Boolean = ExpressionText.of(a) == ExpressionText.of(b)
}

/** The clauses a compiled method carries, as expressions (RFC #2557). */
final case class ContractClauses(requires: List[onion.compiler.AST.Expression], ensures: List[onion.compiler.AST.Expression],
                                 params: List[String] = Nil) {
  def isEmpty: Boolean = requires.isEmpty && ensures.isEmpty
}
