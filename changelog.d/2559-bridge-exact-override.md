### Fixed

- **A call through `G[JInteger]` runs `n(x: JInteger)`, not `n(x: Int)`, when a class declares both (#2559).**
  - Before: `val g: G[JInteger] = new C; g.n(4)` ran the `Int` overload, with no warning.
  - Now: it runs the `JInteger` override, as Java does. `n(Int)` is still used when it is the only candidate.
