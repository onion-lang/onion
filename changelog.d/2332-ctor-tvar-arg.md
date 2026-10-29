### Fixed

- **`new Box[T](u)` with an unrelated `U` is E0000 at the call (#2332).**
  - Before: it compiled, then threw `ClassCastException` at run time.
  - Now: only a `T` (or a variable bounded by it) fits the `T` constructor slot, as in Java.
