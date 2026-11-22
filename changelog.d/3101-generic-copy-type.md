### Breaking

- **A copy of a generic record has the receiver's type arguments, so code that relied on the bare type changes (#3101).**
  - An overloaded call on a copied component picks the typed method: `f(q.x())` ran `f(Object)`, now `f(String)`. Keep the old call with `f(q.x() as Object)`.
  - A copy no longer fits a `P[...]` with other type arguments: `val r: P[Object, Int] = p.copy(y = 3)` with `p: P[String, Int]` compiled, now E0000 (E0005 as an argument). Build that value with `new P[Object, Int](p.x(), 3)`.
  - No `onion migrate` rule; see `docs/migration/pre-1.0.md`.

### Fixed

- **`copy(..)` on a generic record keeps its type arguments.**
  - Before: `new P[String, Int]("s", 1).copy(y = 42).x()` was a bare `A`, so `val s: String = …` was E0000.
  - Now: the copy is a `P[String, Int]`, and `x()` is a `String`.
