### Breaking

- **Four lambda calls on overloaded extensions are now E0006 (#2447).** Each depended on declaration order, or picked differently from the same methods on a class. `docs/migration/pre-1.0.md` lists the four, with the fix for each.
  - Fix: pass a literal of the exact type (`5L`, `"q" as Object`), pass the lambda as a typed value, or rename one method.
- **A lambda that fits both a function-type overload and a SAM overload now takes the function type.**
  - With `outer(f: (Int) -> Int)` beside `outer(f: Function[String, String])`, `"a".outer { p -> ((q: Int) -> p).call(1) }` printed `os:s`; now it prints `oi:1`. To keep the old choice, pass a typed `Function[String, String]` value.

### Fixed

- **A lambda passed to an overloaded extension method picks the overload that takes a function (#2447).**
  - Before: with `def g(f: (Int) -> String)` declared before `def g(s: Int)`, `"a".g((n: Int) -> "n" + n)` was E0000 "type Int is expected". Next to an `Object` overload declared after it, the lambda silently ran the `Object` one.
  - Now: the most specific overload the lambda fits is chosen, in either declaration order. A value block goes to `Callable`, a void block to `Runnable`, and a non-generic overload beats a generic one that ties with it.
- **A warning inside a lambda is reported once, and only for the overload that is chosen (#2452).**
  - Before: a `W0012` in a lambda passed to overloaded methods printed once per overload tried, including overloads that were not chosen.
