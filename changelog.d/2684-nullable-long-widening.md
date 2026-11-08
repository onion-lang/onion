### Fixed

- **`val a: Long? = 5` and `val d: Double? = 5` work (#2684).**
  - Before: compiled, then threw `ClassCastException` (an `Integer` was put in the `Long?` slot).
  - Now: the `Int` is widened first, as `val a: Long = 5` does.
