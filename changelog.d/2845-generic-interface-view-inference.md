### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005, `T` was not inferred through `String`'s `Comparable[String]`.
  - Now: `T = String`, as in Java. Also works for your own class that `conforms Comparable[A]`.
  - An overload that is now applicable can win where it did not before (as in Java): `f(Object)` + `f[T](Comparable[T])` called with `"a"` picks the generic one. `pick[T](Comparable[T], T)` called with `("a", 1)` still falls back to an `Object` overload.
