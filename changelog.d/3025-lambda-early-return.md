### Fixed

- **A block lambda with an early `return` now returns its last expression on the other path.**
  - Before: `(c) -> { if c { return 5 }; 40 }` returned `null` when `c` was false, so `n(false) + 1` threw an NPE. A trailing `if`/`else`, `try` or assignment (`k += 7`) was dropped the same way.
  - Now: the last expression is one more `return`, typed by the same rule as a lambda with no `return`. A last value of the wrong type is now a compile error (E0000, or E0070 for a nullable value), where it used to be a silent `null`. An inferred return type now includes the last expression, so it can widen (for example to `Object`).
