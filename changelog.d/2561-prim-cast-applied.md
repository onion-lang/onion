### Fixed

- **`(5 as Comparable[String])` is a compile error (E0000).**
  - Before: it compiled, then threw ClassCastException when used.
  - Now: a primitive casts to an applied type only if its box has that type, e.g. `5 as Comparable[JInteger]`.
