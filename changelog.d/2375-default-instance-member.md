### Fixed

- **A default argument can read the receiver's fields and the parameters before it (#2375, #2358).**
  - Before: `def f(n: Int, m: Int = k)` with a field `k`, or `def g(n: Int, m: Int = n + 1)`, failed with `VerifyError` when the class loaded; a `private static` field threw `IllegalAccessError`.
  - Now: the default runs in the called method, so `new C(7).f(1)` is `8` and `g(1)` is `3`. A default that names its own or a later parameter is `E0002`, with a hint.
- **A default is checked against its parameter's type (#2541).**
  - Before: `def f(b: Byte = 200)` compiled, then ran with `-56` or crashed when loaded.
  - Now: `E0000`, as for `val b: Byte = 200`. An `Int` constant that fits still narrows (`b: Byte = 3`).
