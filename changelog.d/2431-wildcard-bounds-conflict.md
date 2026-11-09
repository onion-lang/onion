### Fixed

- **`Collections::copy(x, o)` with `x: List[T]` and `o: List[Object]` no longer compiles inside `G[T]` (#2431).**
  - Before: it compiled, then `x[0]` threw `ClassCastException`.
  - Now: E0005 at the call, with its `available:` line; a fitting overload beside it is chosen instead, as in Java.
