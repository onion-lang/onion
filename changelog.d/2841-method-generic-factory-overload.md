### Fixed

- **`c.f(Collections::emptyList())` picks `f(List[T])` over `f(Object)`, as Java does.**
  - Before: `f(Object)` was chosen silently.
  - Now: the factory's element type comes from the parameter, so `f(List[T])` is chosen.
