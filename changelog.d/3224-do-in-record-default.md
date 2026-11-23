### Fixed

- **A record component default written as `do[M] { ... }` works.**
  - Before: `new R(1)` was E0021, the default had vanished.
  - Now: the default runs, as it does on a class or enum parameter.
