### Breaking

- **`[5, null]` with no expected type is now a `List[Int?]`, not a `List[Int]`.**
  - Before: `total([1, null])` compiled for a `List[Int]` parameter, then crashed at the first read.
  - Now: it is E0005 (the argument is a `List[Int?]`); `val ys = ["a", null]; ys[0].length()` needs `?.` or `!!`.
  - Migration: take a `List[Int?]` parameter, or give the literal a target type.
