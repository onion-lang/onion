### Fixed

- **`Collections::copy(x, o)` with `x: List[T]` and `o: List[Object]` is an error inside `G[T]` (#2431).**
  - Before: it compiled, then `x[0]` threw `ClassCastException`.
  - Now: E0000 at the call, as in Java.
