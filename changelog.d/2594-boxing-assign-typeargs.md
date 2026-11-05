### Fixed

- **`val y: Comparable[String] = 5` is a compile error, not a runtime `ClassCastException`.**
  - Before: it compiled, then threw when `y` was used.
  - Now: the type argument is checked against the box's own (`Comparable[JInteger]`), as `5 as Comparable[String]` already was.
