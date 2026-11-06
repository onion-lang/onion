package onion.compiler.contracts

/**
 * Contracts for Java types, which cannot carry `requires` / `ensures` clauses (RFC #2557,
 * design 5). It is built the way the effect table is: a built-in table now, and a table shipped
 * inside a library jar later (not built yet).
 *
 * An entry names a Java interface and the laws its implementations owe. The build checks each
 * concrete implementation it finds against its entry.
 */
object ContractTable {
  /** What an implementation of an interface owes. */
  sealed trait Entry { def interface: String }

  /**
   * `parse(print(v)) == Ok(v)` for each value `v` that `canPrint()` and `printable(v)` admit
   * (design 5.1). L2, `print(parse(t)) == t`, is false in general and is never asserted.
   */
  final case class RoundTrip(interface: String, canPrint: String, printable: String, print: String, parse: String) extends Entry

  /** The built-in entries. */
  val builtIn: Seq[Entry] = Seq(
    RoundTrip("onion.Shape", canPrint = "canPrint", printable = "printable", print = "print", parse = "parse")
  )

  /** The entry for `interface`, if the table holds one. */
  def forInterface(interface: String): Option[Entry] = builtIn.find(_.interface == interface)
}
