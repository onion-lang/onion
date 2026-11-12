### Fixed

- **`i.compareTo("x")` on a `JInteger` is a compile error (#2708).**
  - Before: it compiled, then threw `ClassCastException` at run time.
  - Now: `E0005`, as in Java.
