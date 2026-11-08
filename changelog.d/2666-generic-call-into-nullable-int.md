### Fixed

- **`val a: Int? = ident(5)` compiles when `ident` is generic (#2666).**
  - Before: E0000 "type Int is expected, but type Int? is used". `String?` worked.
  - Now: a generic call's result goes into `Int?`/`Long?`/`Double?`, as it does into `String?`.
