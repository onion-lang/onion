### Fixed

- **`history snap >= old(snap)` (a method called without parentheses) gets W0024, like `snap() >= old(snap())` (#3033).**
  - Before: no warning, so a method that broke the history went unnoticed.
  - Now: each method that writes a field the history may read is warned, as for the spelling with parentheses.
