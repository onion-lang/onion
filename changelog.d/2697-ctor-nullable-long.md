### Fixed

- **`new R(5)` for `record R(x: Long?)` compiles and runs.**
  - Before: `VerifyError` at run time.
  - Now: the `Int` is widened to `Long`, then boxed, as `new R(5L)` and `f(5)` already did.
