### Fixed

- **A lambda that ends in an `if`/`select`/`try` is checked against the call's target type.**
  - Before: `val c: Option[Int] = ch2(() -> { if b { other } else { tail } })` with `def ch2[U](f: () -> U): U` and `other: Option[String]` compiled, then threw ClassCastException where `c` was read.
  - Now: E0000 at `other`, as for the same `if` written without the lambda.
  - A nullable target (`Option[Int]?`) is left as before (#3242).
