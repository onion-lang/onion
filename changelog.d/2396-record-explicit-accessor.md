### Fixed

- **A record method named like a component is a compile error (#2396).**
  - Before: `record R(a: Int) { public: def a(): Int = 42 }` compiled, then died with a JVM `ClassFormatError`. With another return type, the body method was silently ignored.
  - Now: E0010 at the `def`: "record component a: Int in R already generates the accessor a(): Int, so the record body cannot define a() again."
