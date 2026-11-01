### Added

- **W0021: a method can break a `history`; the solver found a counterexample.**
  - `n = n + 1` against `history n >= old(n)` warns with `n = 2147483647` until an `invariant` bounds `n`.
