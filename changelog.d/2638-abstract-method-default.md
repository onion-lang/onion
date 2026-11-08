### Fixed

- **A default on an abstract or interface method can be omitted at the call (#2638).**
  - Before: `abstract def f(n: Int = 5): Int` compiled, then `a.f()` was E0005.
  - Now: when `a` is typed as the declaring class or interface, `a.f()` calls the implementation with `5`.

### Breaking

- **A default on an abstract or interface method is now type-checked (#2638).**
  - Before: `abstract def f(n: Int = "x"): Int` or `def g(n: Int = undefinedThing): Int` in an interface compiled, because the default was never used.
  - Now: E0000 or E0002 at the default. Fix the default. See `docs/migration/pre-1.0.md`.
