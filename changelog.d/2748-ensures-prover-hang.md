### Fixed

- **`onionc` no longer hangs on a method with `requires`/`ensures` (#2748).**
  - Before: `ensures result >= 0` on `bal - n`, with `requires n > 0 && n <= bal`, hung the build, even under `--contracts off`.
  - Now: it is proved in under a second. A formula the prover cannot settle is given up after about two seconds and left unproved (under `--contracts runtime` it keeps its run-time check).
  - Under `--contracts off` the prover does not run; the clause is only reported as E0105.
