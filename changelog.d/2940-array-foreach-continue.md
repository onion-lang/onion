### Fixed

- **`continue` inside `foreach` over an array moves on to the next element (#2940).**
  - Before: `foreach a in args { if a == "x" { continue } ... }` looped forever on the same element, with no error.
  - Now: it skips that element, as it already did for lists and ranges.
