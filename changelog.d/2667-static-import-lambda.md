### Fixed

- **A static-imported function types its lambda: `filterValues(m) { v -> v > 1 }` compiles after `import { onion.Maps::* }` (#2667).**
  - Before: E0052 "lambda parameter v must specify a type", while `Maps::filterValues(m) { v -> v > 1 }` worked.
  - Now: the bare call takes the lambda's parameter types from the imported function, trailing or positional, for class-level and single-method imports alike.
