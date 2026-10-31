### Fixed

- **The E0081 CSV workaround in the docs prints nothing when `--csv` is left out (#2502).**
  - Before: `Strings::split("", ",")` returns `[""]`, so the example printed `Hello, `.
  - Now: the example guards the loop with `if csv != ""`.
