### Added

- **A closed predicate may take parameters; the build samples it.**
  - `def commutes(a: Int, b: Int): Boolean ensures result = a + b == b + a` is run on sampled values; a failing sample is E0113 with the arguments and `--law-seed`/`--law-samples` to reproduce it.
  - A parameter type that cannot be sampled is E0113, not a silent pass. This is the replacement for `law`.
