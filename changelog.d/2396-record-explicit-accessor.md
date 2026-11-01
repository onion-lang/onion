### Fixed

- **A record method named like a component is a compile error (#2396).**
  - Before: `record R(a: Int) { public: def a(): Int = 42 }` compiled, then died with a JVM `ClassFormatError`.
  - Now: E0010 (duplicated method) at the `def`. A body method `a()` with a different return type, which was silently ignored, is E0010 too.
