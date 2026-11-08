### Fixed

- **A default on an abstract or interface method can be omitted at the call (#2638).**
  - Before: `abstract def f(n: Int = 5): Int` compiled, then `a.f()` was E0005.
  - Now: `a.f()` calls the implementation with `5`, whichever subclass `a` is.
