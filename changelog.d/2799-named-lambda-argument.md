### Fixed

- **An untyped lambda passed as a named argument compiles: `ap(f = (x) -> x + "!")`.**
  - Before: E0052 "lambda parameter x must specify a type", though the positional form compiled.
  - Now: the lambda takes its parameter types from the named parameter. Generic parameter types still need a typed lambda.
