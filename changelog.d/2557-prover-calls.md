### Added

- **A history is proved through a call to a pure method that states its contract.**
  - `n = n + step(k)` is proved (or gets a counterexample) from `step`'s `requires` and `ensures`; `--verbose` lists the proof.
  - A callee with no contract, or whose `requires` can fail at the call, is not used: the history is left unproved, never guessed.
