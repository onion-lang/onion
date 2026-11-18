### Fixed

- **A lambda can call an inherited protected method of a Java superclass (#2910).**
  - Before: `() -> { removeRange(0, 1); size() }` in a subclass of `ArrayList` compiled, then threw `IllegalAccessError`.
  - Now: it runs, as the same lambda does in Java.
