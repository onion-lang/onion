### Breaking

- **A `null` element in a `List[Int]` or `Map[String, Int]` literal is E0000 (#2895).**
  - Before: `val xs: List[Int] = [5, null]` compiled; reading `xs[1]` crashed at run time.
  - Now: `[E0000] type Int is expected, but type null is used` at the `null`, with a hint to write `List[Int?]`. See `docs/migration/pre-1.0.md`.
