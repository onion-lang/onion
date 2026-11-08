### Fixed

- **A `history` proved only by assuming the class `invariant` is now W0024, not "proved".**
  - Before: `--verbose` printed `[history] proved` and `--contracts runtime` dropped the guard, even when a constructor broke the invariant (`n = 2147483647` with `invariant n < 10`).
  - Now: W0024 says the proof needs the invariant, which nothing proves yet, and the runtime guard stays.
