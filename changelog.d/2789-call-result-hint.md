### Added

- **`mk()()` now says what to write instead of a bare syntax error.**
  - Before: `Encountered "(", but expecting ")"`, no hint.
  - Now: a hint to bind the result to a `val` first (`val f = mk(); f()`).
