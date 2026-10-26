### Breaking

- **Four lambda calls on overloaded extensions are now E0006 (#2447).** Each one either depended on declaration order or picked differently from the same methods on a class.
  - `z(f: (Object) -> void)` on `Object` beside `z(o: Object)` on `String`, with `"abc".z((x: Object) -> ...)`. Before: it ran the `String` receiver's `z(Object)`. Rename one of the two methods.
  - `c(n: Long, g)` beside `c(n: Object, g)`, with `"a".c(5, (x: Long) -> x * 2L)`. Before: `obj` or `long:10`, depending on order. Pass `5L` to pick the `Long` one.
  - `k(s: Supplier[String])` beside `k(c: Callable[String])`, with `"a".k { "v" }`. Before: `call`. Pass a typed value: `val c: Callable[String] = () -> "v"`, then `"a".k(c)`.
  - `f(o: Object, g)` on `String` beside `f(s: String, g)` on `CharSequence`, with `"a".f("q", (x: Int) -> x)`. Before: `strobj1`. Write `"q" as Object`, or pass the lambda as a typed value.
- **A lambda that fits both a function-type overload and a SAM overload now takes the function type.**
  - With `outer(f: (Int) -> Int)` beside `outer(f: Function[String, String])`, `"a".outer { p -> ((q: Int) -> p).call(1) }` printed `os:s`; now it prints `oi:1`.
  - To keep the old choice, pass a typed value: `val g: Function[String, String] = p -> ...`, then `"a".outer(g)`.

### Fixed

- **A lambda passed to an overloaded extension method picks the overload that takes a function (#2447).**
  - Before: with `def g(f: (Int) -> String)` declared before `def g(s: Int)`, `"a".g((n: Int) -> "n" + n)` was E0000 "type Int is expected". Next to an `Object` overload declared after it, the lambda silently ran the `Object` one.
  - Now: the most specific overload the lambda fits is chosen, in either declaration order. A value block goes to `Callable`, a void block to `Runnable`, and a non-generic overload beats a generic one that ties with it. For a lambda with typed parameters this is the class-method rule.
- **A warning inside a lambda is reported once (#2452).**
  - Before: a `W0012` in a lambda passed to an overloaded method printed once per overload tried, 7 times in a two-level nest.
  - Now: the same warning at the same place prints once.
