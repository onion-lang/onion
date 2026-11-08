### Fixed

- **A top-level build-time predicate runs in its own script, not in another class (#2604).**
  - Before: `def check(): Boolean ensures result` at top level could run a same-named static `check()` of another class, so the result depended on that class (and, across files, on file order).
  - Now: the build runs the predicate the script declares.
