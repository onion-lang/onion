### Fixed

- **A data-enum constant can omit a parameter that has a default.**
  - Before: `enum E(a: Int, b: Int = 7) { X(1) }` was E0021, naming a hidden constructor `E(String, Int, Int, Int)`.
  - Now: `X(1)` gets `b = 7`, as a record does; a default may read an earlier parameter.
- **A named argument in a data-enum constant binds by name.**
  - Before: `enum S(a: Int, b: Int) { Y(b = 5, a = 2) }` put `5` in `a`, by position (#3064).
  - Now: `a = 2, b = 5`, as `new R(...)` does; an unknown or repeated name is E0043/E0044.
