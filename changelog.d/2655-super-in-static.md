### Fixed

- **`super.f()` in a `static def` is a compile error (#2655).**
  - Before: an internal compiler error (`no 'this' pointer within static method`).
  - Now: E0050, as for `this` in a static method.
