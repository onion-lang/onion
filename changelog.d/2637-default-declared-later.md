### Fixed

- **A default argument works when its method is declared below the caller (#2637).**
  - Before: `def run() = g()` above `def g(n: Int = 5)` was E0005, with `available: g(Int)` right below.
  - Now: it compiles and `g()` uses `5`, as if `g` were declared first.
