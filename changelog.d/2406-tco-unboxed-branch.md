### Fixed

- **A self call next to an `Integer` branch is now a tail call (#2406).**
  - Before: with `o: Integer`, `def q(o: Integer, n: Int): Int = if n == 0 { o } else { q(o, n - 1) }` failed with `error: stack overflow` at 200000 calls.
  - Now: it loops. A null `Integer` still throws where it did, so a `try` around such an `if` behaves as before.
