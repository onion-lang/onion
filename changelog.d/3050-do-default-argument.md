### Fixed

- **A `do[Option] { ... }` default argument works.**
  - Before: the call that omitted it was E0005 (or E0021 for a constructor).
  - Now: the block is desugared like any other expression.
