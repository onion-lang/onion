### Fixed

- **`val y: Comparable[String] = 5` is a compile error, not a runtime `ClassCastException`.**
  - Before: it compiled, then threw when `y` was used. `Comparable[Object]` slots and a `T` inferred as `Object` (`g(o, 5)` with `def g[T](b: T, a: Comparable[T])`) were accepted the same way; Java rejects them too.
  - Now: the type argument is checked against the box's own (`Comparable[JInteger]`), as `5 as Comparable[String]` already was. With overloads `f(Comparable[String])` and `f(Object)`, `f(5)` now picks `f(Object)`.
- **`val n: Number = id(5)` compiles, as in Java.**
  - Before: E0000 when a generic call's expected type (`Number`) was wider than the `T` its arguments give (`Integer`).
  - Now: the arguments decide `T`, and the result is checked against the expected type.
