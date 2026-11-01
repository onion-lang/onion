### Fixed

- **`record G(getClass: Class[String])` is a compile error, not a load-time crash.**
  - Before: compiled, then `IncompatibleClassChangeError` when the class loaded.
  - Now: E0010 at the component, saying the accessor would override the final `Object.getClass()`. A type parameter bounded by `Class` (`record G[T extends Class[String]](getClass: T)`) is caught too.
