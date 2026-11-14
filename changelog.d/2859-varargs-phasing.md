### Fixed

- **`pb.command()` on a `ProcessBuilder` returns its `List[String]` (#2859).**
  - Before: E0000, because `command(String...)` was chosen and returned the `ProcessBuilder`.
  - Now: an instance call takes a fixed-arity overload over a varargs one, as in Java. `v.m("a")` picks `m(Object)` over `m(String...)` the same way.
