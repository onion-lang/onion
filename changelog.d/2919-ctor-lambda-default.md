### Fixed

- **`new A(x -> x * 2)` compiles when a later constructor parameter has a default (#2919).**
  - Before: E0052, "lambda parameter x must specify a type", although only one constructor could take the lambda.
  - Now: the lambda takes its type from that constructor's parameter, even when another constructor has the same number of arguments but no function parameter.
