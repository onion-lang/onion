### Fixed

- **`new C[String](Collections::emptyList())` compiles when `C` has `this(List[T])` and `this(Object)` (#2841).**
  - Before: E0021, with the argument shown as `List[Object]`.
  - Now: the `List[T]` constructor is chosen, as in Java. A method call `c.f(Collections::emptyList())` is not changed yet.
