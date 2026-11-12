### Fixed

- **`new A(null)` for an `Int` constructor parameter is a compile error (#2828).**
  - Before: it compiled, then crashed with a null-value error at run time.
  - Now: E0021, as a method call gives E0000 for the same `null`.
