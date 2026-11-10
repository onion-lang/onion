### Fixed

- **A call can omit a default declared later in the file, or in another file (#2653).**
  - Before: `def run() = g()` above `def g(n: Int = 7)` was E0005 outside one class body.
  - Before: the same held for calls into a later class, record, enum, interface, `extension` or file, and for `new B()` with a later `def this(a: Int = 7)` (E0021).
  - Now: declaration order no longer matters, except when a default mentions the name of a top-level `val`/`var`, even inside a lambda. That method must still come before the call.
