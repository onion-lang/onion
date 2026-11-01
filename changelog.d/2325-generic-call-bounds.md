### Fixed

- **A generic method call that breaks a `T extends B` bound is a compile error (#2325).**
  - Before: with `def g[B, T extends B](v: T): B`, `val s: String = g(Integer::valueOf(1))` compiled, then threw a `ClassCastException`.
  - Now: E0000 at the call. Calls where `T` fits `B`, or where nothing fixes `T` (`null`, `[]`, no argument), are unchanged.
