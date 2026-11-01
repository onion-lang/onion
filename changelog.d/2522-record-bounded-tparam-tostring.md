### Fixed

- **`record T[X extends String](toString: X)` is a compile error, not a load-time crash.**
  - Before: compiled, then `ClassFormatError: Duplicate method name "toString"` when the class loaded.
  - Now: E0010 at the component, as for `toString: String`.
