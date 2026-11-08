### Fixed

- **An override with no contract of its own is checked against the `ensures` it inherits.**
  - Before: `override def f(k: Int): Int { return 0 - 1 }` under a base `ensures result >= 0` built with no word.
  - Now: W0025 names the counterexample. The `--contracts runtime` guard for such an override is still open (#2639).
