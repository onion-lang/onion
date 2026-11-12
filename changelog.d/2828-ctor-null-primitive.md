### Fixed

- **`null` for an `Int` constructor parameter is a compile error (#2828).**
  - Before: `new A(null)`, `extends A(null)` and `: this(null)` compiled, then crashed with a null-value error at run time.
  - Now: a compile error (`new` gives E0021; a method call already gave E0000). With a nullable overload, `A(null)` picks it.
