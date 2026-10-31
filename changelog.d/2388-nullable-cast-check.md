### Fixed

- **`(s as Integer)` with `s: String?` is a compile error, like the non-null form (#2388).**
  - Before: it compiled, then threw `ClassCastException` at run time.
  - Now: E0000, because `?` only adds null and the inner types must still fit.
