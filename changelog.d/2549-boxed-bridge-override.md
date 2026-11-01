### Fixed

- **`record R(n: Int) conforms G[JInteger]` compiles again when `G` declares `n(): JInteger` (#2549).**
  - Before: E0104 on `main` since #2385 (unreleased); before that, the program ran.
  - Now: an `Int` method may cover a `JInteger` (or `Number`) method of a generic supertype, which is bridged. `Int` over `Double`, and `JInteger` over `Int`, are still E0104.
