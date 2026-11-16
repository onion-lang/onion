### Fixed

- **A bare `return` works in a lambda whose result type is inferred (#3041).**
  - Before: `(x: Int) -> { if x < 0 { return } ... }` was E0020 "cannot return a value".
  - Now: the lambda is a `void` lambda and `return` exits early.
