### Fixed

- **`p.copy(b = f(), a = g())` runs `f()` first (#2801).**
  - Before: the arguments ran in field order, so `g()` ran first.
  - Now: they run in the order written, as for every other call with named arguments.
