### Fixed

- **`new Lazy[T](() -> 5)` and `extends Lazy[T](() -> 5)` are compile errors when `Lazy` takes a `() -> T` (#3018).**
  - Before: compiled, then threw `ClassCastException` at the first read.
  - Now: E0021, as assigning the same lambda to a `() -> T` value already was. `() -> null` into `() -> T?` still compiles.
