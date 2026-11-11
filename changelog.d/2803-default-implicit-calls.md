### Fixed

- **`v.total`, `a + b` and `v[i]` fill an omitted default argument (#2803).**
  - Before: each compiled, then failed to load with `VerifyError: Bad type on operand stack`.
  - Now: the default is passed, as with `v.total()`. Also `v[i] = x`, `a += b` and `a < b`.
