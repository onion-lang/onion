### Fixed

- **`new Box[T](i)` with `i: Int` is a compile error inside a generic class (#2840).**
  - Before: it compiled, then `new G[String]().wrapInt(3).v` threw `ClassCastException` (an `Integer` read as a `String`). The same held by name, through a default parameter and into a `T...` vararg.
  - Now: E0021, as a method call `take(3)` against `take(t: T)` already gets. `new Box[Integer](3)` and `new Box(3)` still compile.
