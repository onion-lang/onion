### Fixed

- **`10 + (if c { continue } else { i })` compiles: a `break`/`continue` works inside any operand (#2932).**
  - Before: I0000 "Bytecode generation failed", a compiler crash, whenever another operand (a `+` left side, a string concatenation, a receiver, an earlier argument) was already evaluated.
  - Now: the jump leaves or restarts the loop, and the half-evaluated expression is dropped.
