### Fixed

- **A `T extends Comparable[T]` is accepted where a `Comparable[T]` is expected (#2338).**
  - Before: `def asCmp(t: T): Comparable[T] = t` was E0000, and `j.cmp(a, b)` into a `Comparable[T]` parameter was E0005.
  - Now: both compile, as in Java, for `return`, `val`/`var` and call arguments. Other applied types, a raw bound and another variable's bound are still rejected.
