### Changed

- **`--contracts runtime` no longer checks an `ensures` the compiler proved.**
  - Before: every `ensures` became a run-time check.
  - Now: only an `ensures` that was not proved keeps its check.
