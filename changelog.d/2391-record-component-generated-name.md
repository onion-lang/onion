### Fixed

- **`record H(hashCode: Int)` and `record T(toString: String)` are a compile error (#2391).**
  - Before: they compiled, then failed at load with a raw `ClassFormatError`.
  - Now: E0010 at the component, saying it clashes with the method every record generates. Other types (`toString: Int`) still compile, as before.
