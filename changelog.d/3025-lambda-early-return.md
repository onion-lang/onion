### Fixed

- **A block lambda with an early `return` now returns its last expression on the other path.**
  - Before: `(c) -> { if c { return 5 }; 40 }` returned `null` when `c` was false, so `n(false) + 1` threw an NPE.
  - Now: it returns `40`, the same as a lambda with no `return`.
