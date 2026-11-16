### Fixed

- **Named arguments work on a method declared later in the class (#3105).**
  - Before: `b(y = 1, x = k)` above `def b(x: Int, y: Int)` was E0043 "unknown parameter name".
  - Now: it compiles, as when `b` is declared first.
