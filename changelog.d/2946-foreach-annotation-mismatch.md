### Fixed

- **`foreach a: Int in xs` over `String` elements is a compile-time type error (#2946).**
  - Before: it compiled, then failed with a JVM `VerifyError` (array) or a cast error at the wrong line (list).
  - Now: `E0000 type Int is expected, but type String is used`, at the loop variable. The annotation is checked like `element as T`, so whatever `as` accepts still compiles: `foreach i: Long in 1..3`, `Double` over `List[Int]`, `String` over `List[Object]`, `Runnable` over `List[Base]`.
