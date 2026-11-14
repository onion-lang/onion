### Fixed

- **Calling an `(Int) -> T` function value with `null` is a compile error (#2872).**
  - Before: W0012 naming `java.lang.Integer`, then a null-value crash at run time.
  - Now: E0000 "type Int is expected, but type null is used", as for an `Int` method parameter. `(Int?) -> T` still takes `null`.
