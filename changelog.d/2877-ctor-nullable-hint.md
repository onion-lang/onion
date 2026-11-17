### Fixed

- **`new StringBuilder(s)` with `s: String?` now says why it fails (#2877).**
  - Before: E0021 listed `StringBuilder(String)` and never named the `?`.
  - Now: E0021 adds the same hint as E0005 (use `!!`, `?:`, or check for null).
