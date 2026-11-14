### Fixed

- **`new P("a", Integer::valueOf(1))` is a compile error when `P[B, T extends B]` needs `T` to be a `B`.**
  - Before: compiled, then failed at run time with a cast error far from the mistake.
  - Now: a compile error at the `new`, as for a method call and for explicit type arguments.
