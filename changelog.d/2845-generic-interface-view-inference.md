### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005. Now: `T = String`, as in Java; an Onion `enum Color` binds `T = Color`, and a generic overload can win over `f(Object)`.
  - **Breaking:** `g(CharSequence)` + `g[T](Comparable[T])` called with `"a"` is now E0006 (ambiguous), as in Java.
