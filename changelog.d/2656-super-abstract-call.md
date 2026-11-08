### Fixed

- **`super.f()` on an abstract method is a compile error, E0115 (#2656).**
  - Before: it compiled, then threw `AbstractMethodError` when the line ran.
  - Now: E0115 at the call, as in Java. A superclass method with a body is unaffected.
