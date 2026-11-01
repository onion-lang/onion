### Fixed

- **`xs.sort(c)` also rejects `Comparator[? super U]`, and a `Comparator[U]` for a `List[String]` (#2397).**
  - Before: both compiled, then threw `ClassCastException` inside the sort.
  - Now: `E0005` at the call, as in Java. `Comparator[? super T]` for a `List[T]` still works, and so does an inherited `sort(c)` called inside `class Sub[S] extends ArrayList[S]`.
