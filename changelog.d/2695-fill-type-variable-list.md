### Fixed

- **`Collections::fill(x, v)` with `x: List[T]` and `v: Object` is now a compile error (#2695).**
  - Before: it compiled inside a generic class, then threw ClassCastException when `x[0]` was read as a `T`.
  - Now: E0005, as Java does and as Onion already did for a `List[String]`. A `v: T` still compiles.
