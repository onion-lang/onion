### Fixed

- **A type error in a lambda inside `new K(call { ... })` is reported.**
  - Before: no error, then a `VerifyError` (or a silent run of unchecked code).
  - Now: `E0000` at the bad expression, as for `val k = call { ... }`.
