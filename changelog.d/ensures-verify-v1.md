### Added

- **A method's `ensures` is now proved against its body.**
  - Proved: `--verbose` prints `[ensures] proved: C.m`.
  - Counterexample: new warning `W0028`, with the values that break it.
  - `@Trusted` methods are skipped.
