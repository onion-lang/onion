### Fixed

- **`val xs: List[Int?] = [ident(5), null]` compiles (#2693).**
  - Before: E0000, `List[Int?]` expected but `List[Int]` used.
  - Now: it prints `[5, null]`, as the `List[String?]` form already did.
