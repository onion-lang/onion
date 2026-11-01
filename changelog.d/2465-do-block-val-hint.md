### Fixed

- **A `val`/`var` inside `do[...] { }` now gets a hint that compiles (#2465).**
  - Before: "escape `val` with backticks", which is still a syntax error.
  - Now: compute the value inside the `yield`; for `Option`, `Future` and `List` the hint also shows a bind form that compiles, such as `y <- Option::some(x + 1)`.
