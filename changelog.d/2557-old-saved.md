### Added

- **`--contracts runtime` saves `old(e)` at entry.**
  - `ensures n == old(n) + 1` is checked against the value of `n` when the method was entered, not the value after the body ran.
