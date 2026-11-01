### Fixed

- **`= if c { boxed } else { f(..) }` loops instead of overflowing the stack (#2406).**
  - Before: with `o: Integer`, `def q(o: Integer, n: Int): Int = if n == 0 { o } else { q(o, n - 1) }` overflowed at 200000 calls.
  - Now: the self call is a tail call. A null `Integer` still throws where it did, so a `try` around such an `if` behaves as before.
