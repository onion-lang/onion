### Fixed

- **`new A((x, y) -> x + y)` picks the constructor by lambda arity.**
  - Before: E0052 when constructors were overloaded by function arity.
  - Now: the two-parameter constructor is chosen, as for overloaded methods.
