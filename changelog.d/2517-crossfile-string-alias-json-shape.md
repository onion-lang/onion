### Fixed

- **A `type Name = String` from another file works in a record's `shape doc = json` (#2517).**
  - Before: E0005 about `String.onion$$jsonShape()`, a method the user never wrote.
  - Now: `n: Name` reads like `n: String`. An alias the shape still cannot read is E0061.
