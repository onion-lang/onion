### Fixed

- **`new Object().clone()` is E0013 at compile time, not a VerifyError at load (#2900).**
  - Before: a protected method or field reached from a subclass compiled on any receiver, then the script failed to load ("Bad access to protected data").
  - Now: as in Java, the receiver must be the calling class or a subclass of it (E0013 for a method, E0014 for a field). `this`, an unqualified call and a static member are unchanged.
