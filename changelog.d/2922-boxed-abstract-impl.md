### Breaking

- **A primitive parameter no longer implements a non-generic abstract method that takes the boxed class, or the reverse; that is E0037.**
  - Before: `def n(x: Int)` in a class that `conforms I`, where `I` declares `n(x: JInteger)`, compiled. A call through `I` then threw `AbstractMethodError`. Same for `n(x: JInteger)` against `n(x: Int)`.
  - Now: E0037, with a hint naming the spelling that implements it (`Declare n(JInteger)`). A generic supertype (`G[JInteger]` with `n(x: T)`) and a parameter that differs only by `?` still work. See `docs/migration/pre-1.0.md`.
