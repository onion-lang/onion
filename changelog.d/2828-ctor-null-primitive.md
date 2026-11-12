### Fixed

- **`null` for an `Int` constructor parameter is a compile error (#2828).**
  - Before: `new A(null)`, `extends A(null)` and `: this(null)` compiled, then crashed with a null-value error at run time.
  - Now: E0021, as for any constructor that does not fit. With a nullable overload, `A(null)` picks it. A method call `f(null)` keeps its E0000 at the `null`.
