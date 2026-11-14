### Fixed

- **`def w[B, T extends List[B]](b: B, t: T)` accepts a `List[String]` when `B` is `String`.**
  - Before: two `E0000` errors, `List[Object]` expected.
  - Now: it compiles; a `List[Integer]` with `B = String` is still rejected.
- **A generic call that breaks its bound is rejected as the argument of another call too.**
  - Before: `show(w2("q", 3))` with `def w2[B, T extends B]` compiled, then could fail at run time.
  - Now: `E0000`, as `val r = w2("q", 3)` already gave.
