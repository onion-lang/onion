### Added

- **`mk()()` now says what to write instead of a bare syntax error.**
  - Before: `Encountered "(", but expecting ")"`, no hint.
  - Now: the hint quotes your call: `adder(1).call(2)`, or bind it first with a name your file does not use yet (`val f = adder(1)` then `f(2)`).
