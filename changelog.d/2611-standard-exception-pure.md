### Fixed

- **`new IllegalArgumentException("x")` is pure in a `tool` and in contract predicates (#2611).**
  - Before: effect `unknown`, so a `tool` that throws it needed `unknown`, and a predicate that reached it was not run (W0023).
  - Now: the common `java.lang` exceptions are pure, like `RuntimeException`.
