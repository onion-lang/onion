### Fixed

- **`new Lazy[T](() -> 5)` and `extends Lazy[T](() -> 5)` are compile errors when `Lazy` takes a `() -> T` (#3018).**
  - Before: compiled, then threw `ClassCastException` at the first read.
  - Now: E0021 ("constructor not found"); assigning the same lambda to a `() -> T` value was already an error.
  - Still compiles: a lambda that only throws (`() -> throw ...`) or whose only value is `null`.
