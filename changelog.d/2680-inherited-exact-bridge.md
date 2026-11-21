### Fixed

- **`g.n(4)` through `G[JInteger]` runs the inherited `n(x: JInteger)` (#2680).**
  - Before: an own `n(x: Int)` ran instead, and the inherited exact override was ignored.
  - Now: the inherited `n(JInteger)` runs, as in Java, also when it is `final`.
  - The own `n(x: Int)` still runs when the nearest inherited `n(x: JInteger)` is `protected`, `abstract`, or returns an incompatible type.
