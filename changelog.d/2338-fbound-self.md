### Fixed

- **A `T extends Comparable[T]` can be returned or assigned as a `Comparable[T]` (#2338).**
  - Before: `def asCmp(t: T): Comparable[T] = t` was E0000 "type Comparable[T] is expected, but type T is used".
  - Now: it compiles, as in Java. Other applied types, a raw bound, and another variable's bound are still rejected. Passing the `T` as a call argument is still E0005; assign it to a `val` first.
