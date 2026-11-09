### Fixed

- **The `val` hint in a `do` block no longer copies a trailing comment into its fix.**
  - Before: `val y = x + 1 // add one` hinted `Option::some(x + 1 // add one)`, which does not compile.
  - Now: the hint is `y <- Option::some(x + 1)`.
