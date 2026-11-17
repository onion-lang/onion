### Fixed

- **`new A(x -> x * 2)` compiles when a later constructor parameter has a default (#2919).**
  - Before: E0052, "lambda parameter x must specify a type", although the constructor was the only candidate.
  - Now: the lambda takes its type from that constructor's parameter, as the same call on a method does.
