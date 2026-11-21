### Fixed

- **`val xs: Collection[String] = List::of()` compiles (#3017).**
  - Before: E0000, `List[Object]` is not a `Collection[String]`.
  - Now: the expected `Collection[String]` fixes the element type, as in Java. Also `Iterable[T]` targets and `def f(): Collection[T] = Collections::emptyList()`.
