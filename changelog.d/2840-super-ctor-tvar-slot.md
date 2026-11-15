### Fixed

- **`class S[T](ys: List[Number]) extends C[T](ys)` is E0021, not a `ClassCastException` (#2840).**
  - Before: compiled, then threw when `ys` was read as a `List[T]`.
  - Now: the superclass call is checked like `new C[T](ys)`; a primitive into a `T` slot is E0021 too.
