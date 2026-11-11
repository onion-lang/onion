### Breaking

- **A call above overloads with defaults now picks what a call below them picks (#2653).**
  - Before: with `pick()` and `pick(n: Int = 1)` declared below it, `pick()` called `pick()`; below them it called `pick(n: Int = 1)`.
  - Now: `pick()` calls `pick(n: Int = 1)` wherever it is written. Likewise `f(1)` against `f(n: Long)`, `f(n: Int, a: Int = 1)` and `f(n: Int, b: String = "")` is E0006 above them too, as it already was below.
  - To keep the old result, pass the argument or rename a method; there is no `onion migrate` rule. See `docs/migration/pre-1.0.md`.

### Fixed

- **A call can omit a default declared later in the file, or in another file (#2653).**
  - Before: `def run() = g()` above `def g(n: Int = 7)` was E0005 outside one class body. The same held for calls into a later class, record, enum, interface, `extension` or file, for `new B()` with a later `def this(a: Int = 7)` (E0021), and for a default that omits a later method's default (`def f(a: Int = g())` above `def g(b: Int = 2)`).
  - Now: declaration order no longer matters for most defaults.
  - Exception: a default that reads a top-level `val`/`var`, directly or through another default, still needs its function declared above the call. The E0005/E0021 at such a call now lists what to move above it, in order (for example `base`, `g`).
  - Two defaults that call each other are typed in declaration order, as before.
