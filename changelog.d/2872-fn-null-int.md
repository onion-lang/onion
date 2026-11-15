### Breaking

- **`null` into a generic slot whose type argument is a primitive is E0000 (#2872).**
  - Before: W0012. `f(null)` on an `(Int) -> T` crashed at run time; `xs.add(null)` on a `List[Int]` stored `null` and crashed only when read back as an `Int`.
  - Now: `[E0000] type Int is expected, but type null is used`, with a hint to write the type argument as `Int?` (`List[Int?]`, `Map[String, Int?]`). See `docs/migration/pre-1.0.md`.
