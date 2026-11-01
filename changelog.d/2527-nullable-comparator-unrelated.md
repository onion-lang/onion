### Fixed

- **`xs.sort(c)` on a `List[T?]` rejects a `Comparator[U?]` or `Comparator[U]` for an unrelated `U` (#2527).**
  - Before: it compiled, then threw `ClassCastException` at run time.
  - Now: E0005, as for a `List[T]`. A `Comparator[T?]` still sorts a `List[T?]`.
