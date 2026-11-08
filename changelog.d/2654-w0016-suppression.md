### Fixed

- **`--Wno W0016` and `--warn off` silence the W0016 `@TailRecursive` warning (#2654).**
  - Before: both warnings were printed anyway.
  - Now: W0016 follows the same switches as every other warning.
