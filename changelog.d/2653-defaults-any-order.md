### Fixed

- **A call can omit a default declared later in the file, or in another file (#2653).**
  - Before: `def run() = g()` above `def g(n: Int = 7)` was E0005, outside one class body. So were calls into a later class, record, enum, interface or `extension`, `new B()` with a later `def this(a: Int = 7)` (E0021), and `onionc A.on B.on` when `A.on` called into `B.on`.
  - Now: declaration order no longer matters for these calls.
