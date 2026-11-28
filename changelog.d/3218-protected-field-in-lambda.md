### Fixed

- **A lambda can read and write an inherited protected field of a Java superclass in another package (#3218).**
  - Before: `() -> { K++; return K }` in a subclass compiled, then threw `IllegalAccessError`. This held for static fields (`K`, `Base::K = 40`) and instance fields (`inst = inst + 1`).
  - Now: it runs, as the same lambda does in Java.
