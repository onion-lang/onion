### Fixed

- **A lambda keeps its writes to a captured `var` wherever it appears (#3024).**
  - Before: `xs?.forEach((x: Int) -> { s += x })` compiled, but `s` stayed `0`. The same held inside `do`/`while`, a labelled loop, a `select` guard, a `for` initializer, a `try (...)` resource and a `val (a, b) = ...` initializer.
  - Before: a labelled loop whose condition read such a `var` never ended.
  - Now: the lambda shares the variable in every position, so `s` is `6`.
  - Contracts preview: a `history` write inside a lambda in these positions is now checked, so it can report E0109 (or warn W0024) where the build used to pass.
