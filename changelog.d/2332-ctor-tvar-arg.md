### Fixed

- **`new Box[T](u)` with an unrelated type variable `U` is a compile error (E0021), not a `ClassCastException` (#2332).**
  - Before: it compiled, then threw `ClassCastException` at run time.
  - Now: E0021 at the call, unless `U` is bounded by `T` (`[U extends T]`). Other mismatched arguments to a `T` slot (a `String`, an `Object`, a `U?`) are not covered yet; #2332 tracks them. If you meant the old behaviour, write `(u as T)`.
