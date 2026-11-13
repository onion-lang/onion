### Fixed

- **`val y = if c { continue } else { i }` compiles after a lambda in the same method or script (#2913).**
  - Before: I0000 "Continue statement outside of loop" (or "Break ..."), a compiler crash.
  - Now: the jump leaves or restarts the loop, as it does without the lambda.
