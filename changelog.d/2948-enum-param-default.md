### Fixed

- **A data-enum constant can omit a parameter that has a default.**
  - Before: `enum E(a: Int, b: Int = 7) { X(1) }` was E0021, naming a hidden constructor `E(String, Int, Int, Int)`.
  - Now: `X(1)` gets `b = 7`, as a record does; a default may read an earlier parameter.
