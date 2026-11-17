### Fixed

- **A `[T: Numeric]` method can pass its own dictionary on through an instance call (#2790).**
  - Before: `a.total(xs)` inside `twice[T: Numeric]` was E0003, "type instance Numeric[T] is not found".
  - Now: it compiles and forwards `twice`'s dictionary, as `total(xs)` and `Agg::total(xs)` already did.
