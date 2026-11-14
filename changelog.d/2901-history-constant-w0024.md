### Fixed

- **`history 1 >= 0` no longer warns W0024 at every field write (#2901).**
  - Before: `--warn error` rejected a class that compiled cleanly the day before.
  - Now: a history that reads no field and calls no method is silent. One over a method call still warns.
