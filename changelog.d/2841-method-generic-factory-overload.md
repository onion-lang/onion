### Fixed

- **`c.f(Collections::emptyList())` picks `f(List[T])` over `f(Object)`, as Java does (qualified instance calls).**
  - Before: `f(Object)` was chosen silently.
  - Now: the factory's element type comes from the parameter, so `f(List[T])` is chosen.
  - Static and unqualified calls still pick `f(Object)`; tracked in #3307.
