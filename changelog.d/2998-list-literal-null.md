### Breaking

- **`[5, null]` with no expected type is now a `List[Int?]`, not a `List[Int]`.**
  - Before: `total([1, null])` compiled for a `List[Int]` parameter, then crashed at the first read.
  - Now: E0005; `ys[0].length()` on `["a", null]` is E0070 (use `?.` or `!!`). Edits per case: `docs/migration/pre-1.0.md`. Map literals are the separate #3201.
