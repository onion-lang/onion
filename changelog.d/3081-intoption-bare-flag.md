### Fixed

- **`opts.intOption("count", 3)` now fails on a bare `--count`.**
  - Before: returned the default `3`, so a forgotten value ran silently.
  - Now: `IllegalArgumentException: missing value for --count (expected Int)`, like the auto-CLI.
