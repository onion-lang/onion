### Fixed

- **A bare positional call to a superclass's private method is E0013 at compile time (#3012).**
  - Before: `pim(7, 3)` in a subclass compiled, then stopped with `IllegalAccessError` when it ran.
  - Now: E0013, as `this.pim(7, 3)` already was.
- **A bare call skips an inherited private overload for an accessible one (#3012).**
  - Before: with a private `foo(Int)` in the superclass and a public `foo(Object)`, `foo(1)` stopped with `IllegalAccessError`.
  - Now: `foo(1)` calls `foo(Object)`, as `this.foo(1)` does.
