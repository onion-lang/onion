### Fixed

- **A `static` method next to a boxed `override` no longer breaks the override.**
  - Before: `override def n(x: Int)` for `G[JInteger]`, plus `static def n(x: JInteger)`, compiled and then threw `AbstractMethodError`.
  - Now: the instance method is called, as without the static one.
