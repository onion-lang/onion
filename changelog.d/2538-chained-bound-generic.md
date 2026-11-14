### Fixed

- **`def w[B, T extends List[B]](b: B, t: T)` accepts a `List[String]` when `B` is `String`.**
  - Before: two `E0000` errors, `List[Object]` expected.
  - Now: it compiles; a `List[Integer]` with `B = String` is still rejected.
