### Fixed

- **Named arguments work on a callable declared later in the file.**
  - Before: `def a(k: Int): Int = b(y = 1, x = k)` above `def b(x: Int, y: Int)` was E0043 "unknown parameter name". So was a call to a later class, or a later method in a record, enum or interface body.
  - Now: it compiles and binds by name, as when the callee comes first.
