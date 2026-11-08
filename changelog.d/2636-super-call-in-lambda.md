### Fixed

- **`super.f()` inside a lambda runs (#2636).**
  - Before: it compiled, then failed with `VerifyError: Bad invokespecial instruction`.
  - Now: it calls the superclass's `f`, as in Java. Omitted default arguments work too.
