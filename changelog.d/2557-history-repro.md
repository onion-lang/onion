### Added

- **A history counterexample the compiled code reproduces is E0112.**
  - `n = n + 1` against `history n >= old(n)` with no bound on `n` now fails: run from `n = 2147483647`, `n` goes negative.
  - A counterexample that cannot be run, or does not break when run, stays the warning W0021.
  - Bound the state with an `invariant` or a `requires`, or weaken the history.
