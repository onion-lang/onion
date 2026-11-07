### Fixed

- **`super.d(b = 5)` sets `b`, as `this.d(b = 5)` does (#2650).**
  - Before: the name was ignored and `5` went into the first parameter; no error.
  - Now: named arguments on a `super` call bind by name, in any order, and an unknown name is an error.
