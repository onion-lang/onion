### Fixed

- **`do[M] { ... }` works inside an `extension` method and a data-carrying `enum` (#3063).**
  - Before: an extension method failed with `VerifyError` at run time, and an enum constant built from a `do` block was silently `null`.
  - Now: both run, as the same code does in a class.
