### Fixed

- **`new P("a", Integer::valueOf(1))` is a compile error when `P[B, T extends B]` needs `T` to be a `B`.**
  - Before: compiled, then failed at run time with a cast error far from the mistake.
  - Now: a compile error at the `new`, as for a method call and for explicit type arguments.
- **`P[String?, String]` satisfies `class P[B, T extends B]`.**
  - Before: E0000 `type B is expected, but type String is used`, for both `new P[String?, String](null, "x")` and an inferred `new P(ns, "x")`.
  - Now: compiles; a nullable argument for `B` gives `T` the bound `String`.
