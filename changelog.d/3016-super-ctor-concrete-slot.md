### Fixed

- **`class S extends C[Integer]([1.5])` is a compile error (#3016).**
  - Before: it compiled, then the first read of an element threw a `ClassCastException`.
  - Now: E0021, as `new C[Integer]([1.5])` already gives. Arguments that fit, such as `[1, 2]` or `Collections::emptyList()`, still compile.
