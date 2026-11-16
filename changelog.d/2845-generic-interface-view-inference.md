### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005, `T` was not inferred through `String`'s `Comparable[String]`.
  - Now: `T = String`, as in Java. Also works for your own class that `conforms Comparable[A]`.
