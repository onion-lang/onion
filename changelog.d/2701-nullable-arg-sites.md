### Fixed

- **An `Int` for a `Long?` parameter works in `extends P(5)`, `this(5)`, `copy(b = 9)` and with a lambda argument (#2701).**
  - Before: `VerifyError` or `ClassCastException` at run time.
  - Now: the `Int` is widened to `Long`, as in `new P(5)`.
