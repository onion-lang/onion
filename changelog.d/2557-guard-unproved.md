### Changed

- **`--contracts runtime` keeps a guard only for what was not proved.**
  - A `history` the solver proves has no run-time guard. The guards for `invariant`, `ensures` and `requires` stay, since nothing proves them yet.
