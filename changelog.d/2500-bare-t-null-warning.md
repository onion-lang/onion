### Fixed

- **`null` passed to a bare `T` inside its generic class no longer warns W0012 (#2500).**
  - Before: `m(null)`, `new D[T](null)` and `val y: T = null` in `class D[T]` warned, naming `java.lang.Object?`.
  - Now: silent, since a bare `[T]` admits null. `[T extends Object]` and a `D[String]` receiver still warn.
