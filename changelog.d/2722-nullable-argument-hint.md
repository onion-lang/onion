### Fixed

- **A `String?` passed to a `String` parameter now says it is nullable (#2722).**
  - Before: E0005 "not found. Check spelling", with a signature that looked like the call.
  - Now: the same E0005 adds "argument 1 is `String?`, but the parameter takes a non-null `String`", with `!!`, `?:` and a null check as fixes.
