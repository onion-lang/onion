### Fixed

- **A build-time predicate is no longer called false when it is not run fairly (#2604).**
  - Before: a top-level `def check(): Boolean ensures result` could run a same-named method of another class, and a predicate reading a top-level `val` saw it unset; both gave `E0113 ... returned false`.
  - Now: a top-level predicate runs in its own script's class. If it may read a top-level `val`/`var`, `E0113` says it is not checkable (the script sets them when it starts), not that it is false.
