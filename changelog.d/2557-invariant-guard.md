### Added

- **`--contracts runtime` guards `invariant` and `history`.**
  - A class's `invariant` is checked on entry to and on exit from each public instance method; its `history` on exit, against `old(field)` saved at entry.
  - A step the compiler cannot see through (one that calls something) is caught here when it breaks a `history`.
