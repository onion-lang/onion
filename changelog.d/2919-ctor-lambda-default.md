### Fixed

- **`new A(x -> x * 2)` compiles when a later constructor parameter has a default (#2919).**
  - Before: E0052, "lambda parameter x must specify a type", although only one constructor could take the lambda.
  - Now: the lambda takes its type from that constructor's parameter. This applies to a lambda with an untyped parameter; a typed or zero-parameter lambda resolves as before.
  - Unchanged: if another constructor takes `Object` at the lambda's position, the call stays E0052, so adding `: Int` to the lambda never switches constructors.
