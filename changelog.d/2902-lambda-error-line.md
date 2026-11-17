### Fixed

- **An error inside a lambda is reported at its own line (#2902).**
  - Before: `10 / x` as a lambda's last expression was reported at the line above it; a one-line lambda was reported at its caller's line.
  - Now: the report names the line of the failing expression.
