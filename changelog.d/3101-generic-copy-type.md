### Fixed

- **`copy(..)` on a generic record keeps its type arguments.**
  - Before: `new P[String, Int]("s", 1).copy(y = 42).x()` was a bare `A`, so `val s: String = …` was E0000.
  - Now: the copy is a `P[String, Int]`, and `x()` is a `String`.
