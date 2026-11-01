### Fixed

- **A call on `this` inside a generic class sees the class's own `T`, not its bound (#2547, #2536).**
  - Before: in `class J[T extends Comparable[T]]`, `cmp(t)` into a `Comparable[T]` parameter was E0000 `Comparable[Comparable]`; in `class J[T extends Number]`, `first(ys)` passed a `List[Number]` as `List[T]` and threw `ClassCastException` at run time.
  - Now: the first compiles, as `j.cmp(t)` already did; the second is a compile error. Both for `f(x)` and `this.f(x)`, and from inside a lambda.
