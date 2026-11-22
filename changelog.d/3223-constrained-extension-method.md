### Fixed

- **An extension method with a `[T: Trait]` type parameter gets its real arguments (#3223).**
  - Before: `"a".k(3)` passed the trait instance as `x`, so it printed `akShow$$Integer@…`; a body using `Show[T]::show(x)` failed with a null error at run time.
  - Now: `x` is `3` and the instance goes to its own slot, as for a class method.
