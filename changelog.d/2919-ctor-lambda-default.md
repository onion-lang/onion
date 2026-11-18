### Fixed

- **`new A(x -> x * 2)` compiles when a later constructor parameter has a default (#2919).**
  - Before: E0052, "lambda parameter x must specify a type", although only one constructor could take the lambda.
  - Now: the lambda takes its type from that constructor's parameter.
  - Unchanged: if another constructor could also take the lambda (an `Object`, `T`, function or `?` function parameter), the call stays E0052, so adding `: Int` to the lambda never switches constructors.
