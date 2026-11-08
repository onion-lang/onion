### Fixed

- **`--warn error` fails the build on W0021, W0024 and W0025 (#2595).**
  - Before: these warnings are found after type checking, so the build printed them and exited 0.
  - Now: they are errors under `--warn error`, as every other warning is.
