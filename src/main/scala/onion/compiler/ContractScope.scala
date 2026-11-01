package onion.compiler

/**
 * Where a contract expression is being typed (RFC #2557). It decides which special names
 * mean something: `old(e)` is allowed only in a postcondition or a history, and `result`
 * exists only in a postcondition. Outside any contract both are ordinary names.
 */
enum ContractScope:
  case Pre        // `requires`: the parameters and the fields of `this`
  case Post       // `ensures`: the same, plus `result`, and `old(e)`
  case Invariant  // `invariant`: the fields of `this`
  case History    // `history`: the fields of `this`, and `old(e)` for the earlier state
