### Fixed

- **`val l: Long? = j` with `j: Int?` is a compile error, not a crash.**
  - Before: it compiled, then threw `ClassCastException` (or `VerifyError` as a constructor argument). With `j` null it printed `null`.
  - Now: E0000. A nullable primitive is a box, and `Int?` never converts to `Long?`. Null-check first (`if j != null { l = j }`), or keep one primitive type.
- **`(i as Long?)` with `i: Int` gives `5L`, not a `ClassCastException`.** `(k as Long?)` with `k: JInteger` is E0000; `(k as Long)` still converts.
- `f(Int?)` stays more specific than `f(Long?)`: `f(3)` still calls `f(Int?)`.
