### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005, `T` was not inferred through `String`'s `Comparable[String]`.
  - Now: `T = String`, as in Java. Also works for your own class that `conforms Comparable[A]`.
  - An overload that is now applicable can win where it did not before (as in Java): `f(Object)` + `f[T](Comparable[T])` called with `"a"` picks the generic one. If another argument disagrees with that `T`, the `Object` overload still wins: `pick("a", 1)` against `pick[T](Comparable[T], T)`, `pick[T](Comparable[T], Comparable[T])` or `pick[T](Comparable[T], List[T])` (with a `List[Integer]`).
  - The view applies only to a formal's own type argument (`Comparable[T]`), not inside another one (`Comparable[List[T]]`, `List[Comparable[T]]`), and every `...` element must agree on `T`. Otherwise the `Object` overload still wins, as before.
  - `g(CharSequence)` + `g[T](Comparable[T])` called with `"a"` was `g(CharSequence)` and is now E0006 (ambiguous), as in Java.
