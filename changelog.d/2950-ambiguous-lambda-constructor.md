### Fixed

- **An ambiguous constructor call with a lambda says it is ambiguous (#2950).**
  - Before: `new Two((a) -> a + "1", (b) -> b + "2")`, with constructors over `Function` and `UnaryOperator` and none most specific, was E0052 "lambda parameter a must specify a type". Adding the type did not help.
  - Now: E0022 names two of the constructors, as javac does and as E0006 does for the same overloads on a method.
