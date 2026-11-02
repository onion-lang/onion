### Fixed

- **A write that breaks a `history` is caught inside a lambda and on another object (#2597).**
  - Before: `() -> { t = t - 1 }` or `o.t = o.t - 1` (with `o: Clock`) against `history t >= old(t)` compiled with no error and no warning.
  - Now: E0109, as for `t = t - 1` in the method body. A write the checker cannot settle there (a raise, or `cs[0].t = 7`) gets W0024.
