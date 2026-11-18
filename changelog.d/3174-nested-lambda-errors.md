### Fixed

- **A type error in a lambda passed as `p(call { ... })` is reported (#3174).**
  - Before: no error; the program compiled, then failed with `VerifyError` or ran unchecked code.
  - Now: the error is reported where it is (for example `E0000` at the bad value), as for `val s = call { ... }`.
