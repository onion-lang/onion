### Breaking

- **Two lambda calls on overloaded extensions are now E0006 (#2447).**
  - `extension Object { def z(f: (Object) -> void) }` beside `extension String { def z(o: Object) }`: `"abc".z((x: Object) -> ...)` ran the `String` receiver's `z(Object)`. The parameters favour one overload and the receiver the other, so it is ambiguous. Rename one of the two methods.
  - `c(n: Long, g: (Long) -> Long)` beside `c(n: Object, g: (Long) -> Long)`: `"a".c(5, (x: Long) -> x * 2L)` printed `obj` or `long:10`, depending on declaration order. It is E0006, as on a class. Pass `5L` to pick the `Long` one.

### Fixed

- **A lambda passed to an overloaded extension method picks the overload that takes a function (#2447).**
  - Before: with `def g(f: (Int) -> String)` declared before `def g(s: Int)`, `"a".g((n: Int) -> "n" + n)` was E0000 "type Int is expected". Next to an `Object` overload declared after it, the lambda silently ran the `Object` one.
  - Now: the most specific overload the lambda fits is chosen, in either declaration order. A value block goes to `Callable`, a void block to `Runnable`. For a lambda with typed parameters this is the class-method rule.
