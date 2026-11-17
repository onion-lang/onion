### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005, `T` was not inferred through `String`'s `Comparable[String]`.
  - Now: `T = String`, as in Java. A `Timestamp` (a `Comparable[Date]`) binds `T = Date`.
  - A generic overload can now win over `f(Object)`, as in Java. If another argument disagrees with `T`, `f(Object)` still wins.
  - **Breaking:** `g(CharSequence)` + `g[T](Comparable[T])` called with `"a"` is now E0006 (ambiguous), as in Java.
