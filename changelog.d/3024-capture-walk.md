### Fixed

- **A lambda keeps its writes to a captured `var` wherever it appears (#3024).**
  - Before: inside a `?.` call, `do`/`while`, a labelled loop, a `select` guard, a `for` initializer, a `try (...)` resource or a `val (a, b) = ...` initializer, `xs?.forEach((x: Int) -> { s += x })` compiled but `s` stayed `0`; a labelled loop whose condition read such a `var` never ended.
  - Now: the lambda shares the variable in every position, so `s` is `6`.
