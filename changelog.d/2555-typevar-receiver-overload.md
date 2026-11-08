### Fixed

- **Inside a generic class, `f(xs)` picks `f(o: Object)` when `xs` does not fit `f(xs: List[T])` (#2555).**
  - Before: in `class N[T extends Number]`, a `List[Number]` passed through `this` or `me: N[T]` chose `f(List[T])` and failed with E0000.
  - Now: `f(Object)` is chosen, as in Java. A `List[T]` still takes `f(List[T])`.
