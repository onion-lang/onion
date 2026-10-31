### Fixed

- **`record G(getClass: Class[String])` is a compile error, not a load-time crash.**
  - Before: compiled, then `IncompatibleClassChangeError` when the class loaded.
  - Now: E0010 at the component, as for `hashCode: Int` and `toString: String`.
