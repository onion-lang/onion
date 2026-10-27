### Fixed

- **`new Q(null)` now warns W0012 when the parameter is a non-null Onion type, as `q.m(null)` already did (#2464).**
  - Before: no warning for constructors, records or named arguments; the program failed later with `null value used`.
  - Now: W0012 at the `null`. A `String?` parameter or a Java constructor still gets no warning.
