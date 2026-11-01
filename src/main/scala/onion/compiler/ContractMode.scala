package onion.compiler

/**
 * How contract clauses (`requires`, `ensures`, `invariant`, `history`) are treated
 * (RFC #2557, `docs/design/contracts.md` section 6).
 *
 * Contracts are a preview feature, so `Off` is the default. With `Off`, a clause in the
 * source is an error rather than being ignored: a contract that silently does nothing
 * is worse than none.
 */
enum ContractMode:
  case Off      // Contract clauses are rejected, with a hint to enable the flag
  case Runtime  // Clauses are accepted and checked (entry and exit checks)
  case Verify   // Runtime, plus a proof attempt; what is not proved keeps its runtime check

object ContractMode:
  /** The name used on the command line, lower-case. */
  def flagName(mode: ContractMode): String = mode match
    case Off     => "off"
    case Runtime => "runtime"
    case Verify  => "verify"

  def fromName(name: String): Option[ContractMode] = name.toLowerCase match
    case "off"     => Some(Off)
    case "runtime" => Some(Runtime)
    case "verify"  => Some(Verify)
    case _         => None
