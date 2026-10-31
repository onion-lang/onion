### Fixed

- **Two constructors with the same erasure are a compile error (#2498).**
  - Before: `def this(x: T)` beside `def this(x: Object)` compiled, then the class failed to load with `ClassFormatError`.
  - Now: E0035 at the second constructor, as for two methods with the same erasure.
