### Fixed

- **An array passed to an `Object` / `Object...` overload pair picks the same method as Java (#2955).**
  - Before: `H::s(strings)` ran `s(Object)`, and `h.a(ints)` ran `a(Object...)`.
  - Now: a `String[]` takes `s(Object...)`, and an `Int[]`, which is not an `Object[]`, takes `a(Object)`.
