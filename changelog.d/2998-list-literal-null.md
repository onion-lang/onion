### Breaking

- **`[5, null]` with no expected type is now a `List[Int?]`, not a `List[Int]`.**
  - Before: `total([1, null])` compiled for a `List[Int]` parameter, then crashed at the first read.
  - Now: it is E0005 (the argument is a `List[Int?]`); `val ys = ["a", null]; ys[0].length()` is E0070 and needs `?.` or `!!`.
  - Migration: take a `List[Int?]` parameter, or write the target type `List[Int?]` on the literal. For `sort` with a Java comparator, see `docs/migration/pre-1.0.md`.
