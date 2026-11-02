### Added

- **`--contracts runtime` guards each `requires` clause.**
  - A false `requires` throws `onion.ContractViolation` (an `AssertionError`) before the method body runs; the message names the clause and the method.
  - `--contracts check` (the default) is unchanged and adds no guard. Guards for `ensures`, `invariant` and `history` are not built yet. `verify` is still refused.
