### Fixed

- **A lambda keeps its writes to a captured `var` inside `?.` calls, `do`/`while`, labelled loops, `select` guards, `for` initializers and `try (...)` resources (#3024).**
  - Before: `xs?.forEach((x: Int) -> { s += x })` compiled, but `s` stayed `0`; a labelled loop whose condition read such a `var` never ended.
  - Now: the lambda shares the variable in every position, so `s` is `6`.
