### Fixed

- **A class's, record's or method's own `[T]` wins over a module `type T = ...` alias (#2510).**
  - Before: inside `class Box[T]`, `T` meant the alias, so `new Box[String]("x")` was E0021 against a `Box(Int)` nobody wrote.
  - Now: `T` is the type parameter inside the declaration, as in Java and Scala; outside it, the alias still applies.
