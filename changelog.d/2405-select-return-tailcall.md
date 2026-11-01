### Fixed

- **A `select` statement with `return f(..)` in its cases loops instead of overflowing the stack (#2405).**
  - Before: `select l { case Cons(h, t): return sum(t, acc + h); ... }` then `return -1` hit "stack overflow" on a long list.
  - Now: it loops, also when the case body has several statements (`val m = n - 1` then `return f(m, ..)`).
