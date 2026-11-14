### Fixed

- **A class implementing `g(x: T?)` of a generic interface no longer throws `AbstractMethodError` (#2719).**
  - Before: `interface I[T] { def g(x: T?): String }` with `def g(x: String?)` compiled, then crashed on `i.g("a")`.
  - Now: the bridge method is generated, so the call prints `got a`.
