### Fixed

- **A block lambda with an early `return` now returns its last expression on the other path.**
  - Before: `(c) -> { if c { return 5 }; 40 }` returned `null` when `c` was false, so `n(false) + 1` threw an NPE. A trailing `if`/`else` or `try` was dropped the same way.
  - Now: the last expression is one more `return`, typed by the same rule as a lambda with no `return`. A last value of the wrong type is now E0000 at compile time, where it used to be a silent `null`.
