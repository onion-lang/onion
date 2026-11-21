### Fixed

- **A local function value is called instead of a default-imported function of the same name.**
  - Before: `val first: (List[Int]) -> Int = (ys) -> 42` then `first([1, 2, 3])` printed `1`; `map([1, 2], x -> x * 2)` beside a local `map` printed `[2, 4]`.
  - Now: both call the local (`42`), with any argument, a lambda included. A local whose type does not fit is an error (E0005, or E0070 when it is nullable), not a call to the import. A same-named method of the enclosing class still comes first. Fields are not covered: a function-typed field still loses to an import.
