### Fixed

- **A lambda parameter read by a nested lambda no longer crashes the compiler when a captured variable elsewhere has the same name (#2462).**
  - Before: `run { p -> run { q -> "" + p } }` next to a captured `val p` failed with I0000 (`Index -1 out of bounds`).
  - Now: it compiles, and the parameter keeps its own value.
