### Breaking

- **A lambda passed to overloaded extension methods no longer depends on declaration order (#2447).**
  - A call that compiled may now run a different overload (mostly with untyped lambda parameters), or be E0006 where two fit equally. `docs/migration/pre-1.0.md` gives examples and the fixes: type the lambda's parameters, pass the other argument with `as`, pass the lambda as a typed value, or rename a method.

### Fixed

- **A lambda passed to an overloaded extension method picks the overload that takes a function (#2447).**
  - Before: with `def g(f: (Int) -> String)` declared before `def g(s: Int)`, `"a".g((n: Int) -> "n" + n)` was E0000 "type Int is expected". Next to an `Object` overload declared after it, the lambda silently ran the `Object` one.
  - Now: the most specific overload the lambda fits is chosen, in either declaration order. A value block goes to `Callable`, a void block to `Runnable`, and a non-generic overload beats a generic one that ties with it.
- **A warning inside a lambda is reported once, and only for the overload that is chosen (#2452).**
  - Before: a `W0012` in a lambda passed to overloaded methods printed once per overload tried, including overloads that were not chosen.
