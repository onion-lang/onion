### Fixed

- **`val l: Long? = j` with `j: Int?` is a compile error, not a crash.**
  - Before: it compiled, then threw `ClassCastException` (or `VerifyError` as a constructor argument).
  - Now: E0000. A nullable primitive is a box, and `Int?` never converts to `Long?`.
