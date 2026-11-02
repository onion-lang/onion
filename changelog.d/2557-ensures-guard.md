### Added

- **`--contracts runtime` guards `ensures`.**
  - A false `ensures` throws `onion.ContractViolation` from any `return` of the method; the message names the clause and the method.
  - A clause it cannot enforce yet (`old(...)`, a static, generic or variadic method) is the warning W0026, never silent.
