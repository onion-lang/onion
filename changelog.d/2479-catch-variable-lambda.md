### Fixed

- **A lambda inside a `catch` block can use the caught exception (#2479).**
  - Before: `catch e: Exception { run { q -> e.getMessage() } }` compiled, then failed with `java.lang.VerifyError`.
  - Now: the lambda reads `e`, and an assignment to `e` inside it is visible after the call.
