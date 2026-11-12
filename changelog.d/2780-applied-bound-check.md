### Fixed

- **A type argument that fails its bound's type arguments is a compile error (#2780).**
  - Before: `f[C extends Animal[C]]` took a `Cat` that is an `Animal[Dog]`, and `m[T extends Comparable[String]]` took an `Integer`. Both compiled, then threw `ClassCastException`.
  - Now: the call is rejected (E0000 or E0005), as javac does; so is `new H[JInteger]()` for `class H[T extends Comparable[String]]`.
