### Breaking

- **A named call above overloads declared later picks what a call below them picks (#3110).**
  - `f(x = 1)` above `def f(x: Int)` and `def f(x: Long, y: Int = 2)` ran the `Long` overload, now the `Int` one.
  - Above `f(x: Int)` and `f(x: Int, y: Int = 2)`, `f(x = 1)` is now E0006; above `def b(x: Int, y: Int)`, `b(arg0 = 1, arg1 = 2)` is now E0043.
  - No `onion migrate` rule; see `docs/migration/pre-1.0.md`.

### Fixed

- **Named arguments work on a callable declared later in the file.**
  - Before: `def a(k: Int): Int = b(y = 1, x = k)` above `def b(x: Int, y: Int)` was E0043 "unknown parameter name". So was a call to a later class, or a later method in a record, enum or interface body.
  - Now: it compiles and binds by name, as when the callee comes first.
