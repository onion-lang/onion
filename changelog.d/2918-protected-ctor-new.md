### Fixed

- **`new P()` for a protected constructor of a class in another package is E0013.**
  - Before: it compiled, then the class failed to load with `VerifyError`.
  - Now: a compile error, as in Java. `: super(...)` still reaches it.
