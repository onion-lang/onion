### Breaking

- **A lambda call is E0006 when one extension overload has the more specific parameters and another the more specific receiver (#2447).**
  - Before: `extension Object { def z(f: (Object) -> void) }` beside `extension String { def z(o: Object) }` made `"abc".z((x: Object) -> ...)` run one of them, depending on declaration order.
  - Now: E0006. Rename one of the two methods.

### Fixed

- **A lambda passed to an overloaded extension method picks the overload that takes a function (#2447).**
  - Before: with `def g(f: (Int) -> String)` declared before `def g(s: Int)`, `"a".g((n: Int) -> "n" + n)` was E0000 "type Int is expected".
  - Before, too: next to an `Object` overload declared after it, the lambda silently ran the `Object` one.
  - Now: the most specific overload the lambda fits is chosen, in either declaration order. For a lambda with typed parameters this is the class-method rule.
