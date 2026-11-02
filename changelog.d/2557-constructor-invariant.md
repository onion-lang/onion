### Added

- **`--contracts runtime` checks a class's `invariant` when its constructor finishes.**
  - `new Account(-3)` throws `ContractViolation` when `invariant balance >= 0` does not hold.
  - A constructor with a `return` of its own, and a record's compiler-written constructor, are named by the warning W0026.
