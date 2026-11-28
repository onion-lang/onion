### Fixed

- **A `var` declared inside a block default argument can be assigned.**
  - Before: `def t(n: Int = { var a = 0; a = 5; a })` gave E0036 "cannot assign to val a".
  - Now: it compiles and `t()` is `5`, as in a method body.
