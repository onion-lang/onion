### Fixed

- **A write that breaks a `history` is caught inside a lambda and on another object (#2597).**
  - Before: `() -> { t = t - 1 }` or `o.t = o.t - 1` (with `o: Clock`) against `history t >= old(t)` compiled with no error and no warning.
  - Now: E0109, as for `t = t - 1` in the method body, also through `o: Clock?`, `o: Box[T]` or a type alias.
  - A write the checker cannot settle (`cs[0].t = 7`, an untyped lambda parameter) gets W0024 at its line, saying why; it is never a false E0109.
