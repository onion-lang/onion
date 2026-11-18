### Fixed

- **A local function value named `first` or `last` is called, not the default-imported one.**
  - Before: `val first: (List[Int]) -> Int = (ys) -> 42` then `first([1, 2, 3])` printed `1`.
  - Now: it prints `42`; a local shadows an import, as in Java and Python.
