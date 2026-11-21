### Fixed

- **`g.n(4)` through `G[JInteger]` runs the inherited `n(x: JInteger)` (#2680).**
  - Before: an own `n(x: Int)` ran instead, and the inherited exact override was ignored.
  - Now: the inherited `n(JInteger)` runs, as in Java.
