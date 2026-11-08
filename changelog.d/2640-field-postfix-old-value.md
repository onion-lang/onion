### Fixed

- **`obj.n++` on an instance field yields the old value, as in Java.**
  - Before: `val id = k.n++` gave the new value (5 -> 6), unlike a local, static field or array element.
  - Now: it gives 5; `n--` likewise.
