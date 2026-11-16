### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005, `T` was not inferred through `String`'s `Comparable[String]`.
  - Now: `T = String`, as in Java. Also works for your own class that `conforms Comparable[A]`.
  - An overload that is now applicable can win where it did not before (as in Java): `f(Object)` + `f[T](Comparable[T])` called with `"a"` picks the generic one. If another argument disagrees with that `T`, the `Object` overload still wins: `pick("a", 1)` against `pick[T](Comparable[T], T)`, `pick[T](Comparable[T], Comparable[T])` or `pick[T](Comparable[T], List[T])` (with a `List[Integer]`).
