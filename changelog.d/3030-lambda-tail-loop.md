### Fixed

- **A lambda whose last statement is a `do`/`while` or a labelled loop now runs the loop.**
  - Before: it compiled, then silently did nothing.
  - Now: the loop runs, as it does when another statement follows it.
