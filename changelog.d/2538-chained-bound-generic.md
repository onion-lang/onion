### Fixed

- **`def w[B, T extends List[B]](b: B, t: T)` accepts a `List[String]` when `B` is `String`.**
  - Before: two `E0000` errors, `List[Object]` expected.
  - Now: it compiles; a `List[Int]` with `B = String` is still rejected.
- **A generic call that breaks its bound is rejected wherever it appears, including as a constructor argument.**
  - Before: `new H(w2("q", 3))` or `show(w2("q", 3))` with `def w2[B, T extends B]` compiled, then failed at run time with a `ClassCastException`.
  - Now: one `E0000` at the call, as `val r = w2("q", 3)` already gave.
