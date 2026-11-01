### Fixed

- **`new Box[T](u)` with an unrelated `U` is a compile error (E0021), not a `ClassCastException` (#2332).**
  - Before: it compiled, then threw `ClassCastException` at run time.
  - Now: E0021 at the call. Only a `T` (or a variable bounded by it) fits the `T` constructor slot, as in Java.
