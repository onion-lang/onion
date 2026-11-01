### Added

- **The history prover models `Int` locals and a trailing `return`.**
  - `val d = k * 2` then `n = n + d` is now proved (or gets a counterexample) instead of being skipped.
