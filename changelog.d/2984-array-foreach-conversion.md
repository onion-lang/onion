### Fixed

- **`foreach a: Long in ints` over an `Int[]` compiles and runs (#2984).**
  - Before: a loop annotation that differs from the array's element type failed with I0000 or a `VerifyError`.
  - Now: each element is converted as `as` would (widen, narrow, box, unbox), e.g. `Int` to `Long` or `Int` to `Object`.
