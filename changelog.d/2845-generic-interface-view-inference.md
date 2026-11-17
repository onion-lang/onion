### Fixed

- **`id("a")` compiles against `def id[T](c: Comparable[T])` (#2845).**
  - Before: E0005. Now: `T = String`, as in Java. An Onion `enum Color` binds `T = Color`.

### Breaking

- **A generic overload now competes with a plain one, as in Java (#2845).**
  - `g(CharSequence)` beside `g[T](Comparable[T])`, called as `g("a")`: before `charseq`, now E0006 (ambiguous). Write `g("a" as CharSequence)`; the hint shows it.
  - `f(Object)` beside `f[T](Comparable[T])`, called as `f("a")`: before `object`, now `generic`. See `docs/migration/pre-1.0.md`.
