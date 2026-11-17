### Added

- **`mk()()` now says what to write instead of a bare syntax error.**
  - Before: `Encountered "(", but expecting ")"`, no hint.
  - Now: a hint naming your function: bind it first (`val f = mk(...)` then `f()`), or call it in one expression with `mk(...).call()`.
