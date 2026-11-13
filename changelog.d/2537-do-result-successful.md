### Fixed

- **`y <- Result::ok(x + 1)` and `y <- Result::successful(x + 1)` compile inside `do[Result]`.**
  - Before: E0000, the error type was inferred as `Object`.
  - Now: the bind means `val y = x + 1`, so the block takes its error type from the other binds. Only `onion.Result` is affected; a class of your own named `Result` keeps its `bind` calls.
