### Breaking

- **`--contracts` now defaults to `check`.**
  - Before: a contract clause (`requires`, `ensures`, `invariant`, `history`) was E0105 unless you passed `--contracts check`.
  - Now: clauses are accepted. `--contracts off` restores the old behaviour.
