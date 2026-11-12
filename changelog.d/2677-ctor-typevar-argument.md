### Fixed

- **`new C[T](xs)` checks `xs` against `T` itself, not `T`'s bound (#2677).**
  - Before: inside `class D[T extends Number]`, `new C[T](xs)` with `xs: List[Number]` compiled, then threw `ClassCastException` when an element was read.
  - Now: E0021, the same check a method call `f(xs)` gets. With a `this(o: Object)` overload beside `this(xs: List[T])`, that one is chosen, as in Java.
