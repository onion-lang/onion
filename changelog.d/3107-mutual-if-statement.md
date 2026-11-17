### Fixed

- **A `@TailRecursive` mutual group with an `if` statement before its tail call returns the right value.**
  - Before: `if k == 3 { IO::print("") }` followed by `return ev(k - 1, ..)` made the group return `null` (or `0`/`false`), with no warning.
  - Now: the tail call runs after the `if`, as it does without the annotation.
