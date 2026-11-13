### Fixed

- **`f(3) { x -> ... }` and `f(3) { x, y -> ... }` each call their own overload (#2823).**
  - Before: with `f(Int, (Int) -> Int)` and `f(Int, (Int, Int) -> Int)`, the second call often reused the first call's overload. A statement vanished with no error, or a valid lambda got E0101.
  - Now: the overload whose function type takes as many parameters as the lambda is chosen, in any order.
