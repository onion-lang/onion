### Added

- **`--contracts runtime` guards a generic and a variadic method.**
  - Their `requires` and `ensures` are checked like any other method's.
  - The class's `invariant` is not yet checked when a constructor finishes; the warning W0026 names it.
