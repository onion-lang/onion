### Added

- **`mk()()` now says what to write instead of a bare syntax error.**
  - Before: `Encountered "(", but expecting ")"`, no hint.
  - Now: the hint quotes your first call and the second call's arguments: `val g = adder(1)` then `g(2)`, or `adder(1).call(2)`.
