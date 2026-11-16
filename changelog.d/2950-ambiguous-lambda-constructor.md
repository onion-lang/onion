### Fixed

- **An ambiguous constructor call with an untyped lambda says it is ambiguous (#2950).**
  - Before: `new Two((a) -> a + "1", (b) -> b + "2")`, with constructors over `Function` and `UnaryOperator` and none most specific, was E0052 "lambda parameter a must specify a type". Adding the type did not help.
  - Now: E0022 names two of the constructors, as javac does. A typed or zero-parameter lambda still picks the Onion function type, as before.
