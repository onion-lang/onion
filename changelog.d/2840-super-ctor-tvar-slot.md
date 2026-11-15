### Fixed

- **`class S[T extends Number](ys: List[Number]) extends C[T](ys)` is E0021, not a `ClassCastException` (#2840).**
  - Before: compiled, then threw when `ys` was read as a `List[T]`.
  - Now: the parent constructor is chosen as before, then each argument is checked against its `T` slot like `new C[T](ys)`; a primitive into a `T` slot is E0021 too.
  - An argument that takes its type from the slot still compiles: `Collections::emptyList()`, `[]`, `() -> null`.
