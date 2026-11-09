### Fixed

- **`(i as Long?)` on an `Int?` is a compile error, not a crash at run time.**
  - Before: it compiled, then always threw `ClassCastException`.
  - Now: E0000 at the cast, as Java rejects `(Long) anInteger`. `Int? as Int` and `Int? as Object` still work.
