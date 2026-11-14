### Fixed

- **`foreach a: Int in xs` over `String` elements is a compile-time type error (#2946).**
  - Before: it compiled, then failed with a JVM `VerifyError` (array) or a cast error at the wrong line (list).
  - Now: `E0000 type Int is expected, but type String is used`, at the loop variable. Widening, boxing and a checked narrowing (`String` over `List[Object]`) still work.
