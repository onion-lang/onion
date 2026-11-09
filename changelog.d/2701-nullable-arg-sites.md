### Fixed

- **An `Int` for a `Long` or `Long?` parameter works in `extends P(5)`, `this(5)`, `copy(b = 9)` and with a lambda argument (#2701, #2704).**
  - Before: `VerifyError` or `ClassCastException` at run time.
  - Now: the `Int` is widened to `Long` (or `Double`), as in `new P(5)`.
