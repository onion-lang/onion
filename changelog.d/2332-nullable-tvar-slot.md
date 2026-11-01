### Breaking

- **A `T?` slot (field, variable, argument, return, constructor argument) no longer takes an unrelated value (#2332).**
  - Before: `o = u`, `o = uo` or `o = "x"` into `o: T?` compiled, then threw `ClassCastException` where the value was read back as a `T`.
  - Now: `E0000 type T? is expected, but type U is used.` (`E0021` for a constructor argument, as for a `T` parameter). A `T`, a `T?` and `null` still fit; write `(u as T)` if the value really is a `T`.
