### Fixed

- **A bare call to a superclass's private method is E0013 at compile time (#3012).**
  - Before: `pim(7, 3)`, `pim { x -> x }`, `pim((x) -> x)` or `pim(b = 3, a = 7)` in a subclass compiled, then stopped with `IllegalAccessError` when it ran.
  - Now: E0013, as `this.pim(7, 3)` already was.
