### Breaking

- **A lambda that ends in an `if`/`select`/`try` is checked against the call's target type.**
  - Before: `val c: Option[Int] = ch2(() -> { if b { other } else { tail } })` (`ch2[U](f: () -> U)`, `other: Option[String]`) compiled, then threw `ClassCastException` where `c` was read. A `List[String]` branch into a `List[Object]` target ran.
  - Now: E0000 at the branch that does not fit. A nullable target (`Option[Int]?`) is unchanged. See `docs/migration/pre-1.0.md` (#3241).
