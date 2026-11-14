### Fixed

- **`null` into an `Int`-typed slot of a generic is a compile error (#2872).**
  - Before: W0012 naming `java.lang.Integer`, then a null-value crash at run time.
  - Now: E0000 "type Int is expected, but type null is used", as for an `Int` method parameter. Covers `f(null)` on an `(Int) -> T`, `xs.add(null)` on a `List[Int]`, `g.apply(null)` on a `Function[Int, R]` and a user `Box[Int].put(null)`. `(Int?) -> T` and `List[Int?]` still take `null`.
