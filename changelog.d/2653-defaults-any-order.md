### Fixed

- **A call can omit a default declared later in the file, or in another file (#2653).**
  - Before: `def run() = g()` above `def g(n: Int = 7)` was E0005 outside one class body. The same held for calls into a later class, record, enum, interface, `extension` or file, for `new B()` with a later `def this(a: Int = 7)` (E0021), and for a default that omits a later method's default (`def f(a: Int = g())` above `def g(b: Int = 2)`).
  - Now: declaration order no longer matters, except for a default that reads a top-level `val`/`var` (even inside a lambda), or calls a method whose default does. Such a default is typed in source order, as before.
