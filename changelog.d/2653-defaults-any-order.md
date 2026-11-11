### Fixed

- **A call can omit a default declared later in the file, or in another file (#2653).**
  - Before: `def run() = g()` above `def g(n: Int = 7)` was E0005 outside one class body. The same held for calls into a later class, record, enum, interface, `extension` or file, for `new B()` with a later `def this(a: Int = 7)` (E0021), and for a default that omits a later method's default (`def f(a: Int = g())` above `def g(b: Int = 2)`).
  - Now: declaration order no longer matters for most defaults.
  - Exception: a default that reads a top-level `val`/`var`, directly or through another default, is typed in source order, as before. Declare that function above calls that omit it; the E0005/E0021 at such a call now says which name holds the default back. Matching is by name, so a call to a different method with the same name also counts.
  - A default now picks an overload the way a call in a method body does: `def f(x: Object = pick())` with `pick()` and `pick(n: Int = 1)` calls `pick(n: Int = 1)`.
