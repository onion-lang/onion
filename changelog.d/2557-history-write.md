### Added

- **A write that can break a `history` is E0109.**
  - `ResettableCounter.reset` (`n = 0`) breaks `history n >= old(n)` because `Counter.inc` raises `n`.
  - Reported only when the syntax settles it; a class whose only write is `n = 0` is fine.
