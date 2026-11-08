### Fixed

- **`--contracts runtime` catches a `history` broken by a setter whose parameter has the field's name (#2633).**
  - Before: `history n >= old(n)` with `def set(n: Int) { this.n = n }` read the parameter, so `set(3)` passed.
  - Now: the guard reads the field, and `set(3)` throws `ContractViolation`.
