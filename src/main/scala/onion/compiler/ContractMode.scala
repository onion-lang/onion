package onion.compiler

/**
 * How contract clauses (`requires`, `ensures`, `invariant`, `history`) are treated
 * (RFC #2557, `docs/design/contracts.md` section 6).
 *
 * Contracts are a preview feature, so `Off` is the default. With `Off`, a clause in the
 * source is an error rather than being ignored: a contract that silently does nothing
 * is worse than none.
 *
 * A mode is named for what it does. `Check` is what exists: the clauses are typed, and the
 * static checks that need no solver run on them. `Runtime` and `Verify` are reserved for the
 * guards and the proofs; until they exist the command line refuses them, so no mode promises
 * a check it does not make.
 */
enum ContractMode:
  case Off      // Contract clauses are rejected, with a hint to enable the flag
  case Check    // Clauses are typed and statically checked; nothing is enforced at run time
  case Runtime  // Reserved: guards for what was not proved
  case Verify   // Reserved: proofs

object ContractMode:
  /** The names the command line accepts today, lower-case. */
  val accepted: Seq[ContractMode] = Seq(Off, Check)

  /** The name used on the command line, lower-case. */
  def flagName(mode: ContractMode): String = mode match
    case Off     => "off"
    case Check   => "check"
    case Runtime => "runtime"
    case Verify  => "verify"

  /** Parses a name. A reserved mode parses (so the error can say it is not built yet) but is not accepted. */
  def fromName(name: String): Option[ContractMode] = name.toLowerCase match
    case "off"     => Some(Off)
    case "check"   => Some(Check)
    case "runtime" => Some(Runtime)
    case "verify"  => Some(Verify)
    case _         => None

  /** True when the mode does something today. */
  def isAvailable(mode: ContractMode): Boolean = accepted.contains(mode)
