### Fixed

- **A `[T: Trait]` method can call another one through `super.`, an extension, or a top-level function (#3156).**
  - Before: `super.total(xs)`, `xs.tot(ys)`, `xs?.tot(ys)`, `sum(xs)` from a class method, or a named-argument call inside a `[T: Numeric]` method gave E0003 "instance Numeric[T] is not found".
  - Now: the caller's own instance is passed on, as `a.total(xs)` already did.
