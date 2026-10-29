### Fixed

- **A `val`/`var` inside `do[...] { }` now gets a hint that compiles (#2465).**
  - Before: "escape `val` with backticks", which is still a syntax error.
  - Now: the hint says a do block holds only `x <- expr`, expressions and `yield`, and shows the `y <- Option::some(...)` form.
