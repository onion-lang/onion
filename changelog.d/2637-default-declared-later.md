### Fixed

- **A default argument works when its method is declared later in the same class (#2637).**
  - Before: in `class A { def run(): Int = g()  def g(n: Int = 5): Int = n }`, `g()` was E0005, with `available: g(Int)` right below.
  - Now: it compiles and `g()` uses `5`, as if `g` were declared first. Top-level functions, records and other classes still need the declaration first (#2653).
