### Fixed

- **A lambda can be a `UnaryOperator` or `BinaryOperator` (#2844).**
  - Before: `val f: UnaryOperator[String] = (s) -> s + "!"` and `xs.replaceAll((s) -> ...)` were E0005 `.call(Object)`.
  - Now: they compile, as in Java. This covers any interface whose one abstract method is inherited.
  - A constructor overloaded on `Function` and `UnaryOperator` now picks `UnaryOperator` for a lambda, as Java does (before: `Function`).
