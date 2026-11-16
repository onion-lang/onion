### Fixed

- **`val l: Long? = ident(5)` reports one error, the right way round.**
  - Before: two E0000s; the first said "Int is expected, but Long? is used" and suggested `!!`.
  - Now: one E0000, "Long? is expected, but Int is used".
