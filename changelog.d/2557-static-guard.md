### Added

- **`--contracts runtime` guards a static method.**
  - `requires` and `ensures` of a static method are checked like an instance method's; the class's `invariant` and `history` are not, since they speak about an object.
  - A generic or variadic method is still unguarded, with the warning W0026.
