### Fixed

- **`--contracts runtime` guards a superclass `history` in subclass methods (#2731).**
  - Before: a `Sub` method that wrote the inherited field broke the `history` with no `ContractViolation`.
  - Now: it throws, like a `Base` method does.
