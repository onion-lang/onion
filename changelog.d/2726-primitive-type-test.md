### Fixed

- **`x is JLong` and `case s is String:` on a primitive value no longer crash with `VerifyError`.**
  - Before: the program compiled, then the JVM rejected the class.
  - Now: the value is boxed first, so `5L is JLong` is `true` and `5 is String` is `false`.
