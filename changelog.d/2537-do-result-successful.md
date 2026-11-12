### Fixed

- **`y <- Result::successful(x + 1)` compiles inside `do[Result]`.**
  - Before: E0000, the error type was inferred as `Object`.
  - Now: the bind is a plain `val y = x + 1`, so the block takes its error type from the other binds.
