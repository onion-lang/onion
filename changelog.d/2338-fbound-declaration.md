### Fixed

- **An F-bounded parameter can fill another F-bound (#2338).**
  - Before: `class Holder[A extends Animal[A], C extends Animal[C]]`, `G::f(xs)` from inside `g[U extends Comparable[U]]`, and `derive!(Json)` on `record B[T extends Comparable[T]]` were E0000/E0005.
  - Now: they compile. `U extends Comparable[String]` passed to `f[T extends Comparable[T]]` is still rejected.
