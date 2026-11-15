### Fixed

- **`u() as Object` and `select u() { ... }` on a `void` call report E0027 (#2891).**
  - Before: an internal compiler error (I0000).
  - Now: `E0027` at the call, as for `val o: Object = u()`.
