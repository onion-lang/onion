### Breaking

- **A `T?` field or variable no longer takes an unrelated value (#2332).**
  - Before: `o = u`, `o = uo` or `o = "x"` into `o: T?` compiled, then threw `ClassCastException` where the value was read back as a `T`.
  - Now: `E0000 type T? is expected, but type U is used.` A `T`, a `T?` and `null` still fit; write `(u as T)` if the value really is a `T`.
