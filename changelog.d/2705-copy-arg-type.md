### Fixed

- **A record `copy` argument of the wrong type is a compile error (#2705).**
  - Before: `n.copy(b = "s")` on `b: Long?` compiled, then threw `ClassCastException` when it ran.
  - Now: E0000 at the argument, as a mistyped named method argument gives. `copy(z = 1)` with no component `z` reports one error, not two. A record that declares its own `copy` still calls it.
